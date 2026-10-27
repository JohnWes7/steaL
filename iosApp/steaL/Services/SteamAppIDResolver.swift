//
//  SteamAppIDResolver.swift
//  steaL
//
//  Created by yijiang on 2026/10/1.
//

import Foundation
import OSLog

/// 把一批 ITAD 游戏 UUID 换成 Steam appID，供首页与 Hot Deals 列表共用。
/// Resolves a batch of ITAD game UUIDs into Steam appIDs, shared by the home page and the Hot Deals list.
///
/// ITAD 只给候选，一个游戏可能有多个 `app/`，第一个不一定还存在，所以多候选时要再向 Steam 校验一次；
/// 这件事只依赖 ITAD 与 Steam 两个接口、不依赖页面状态，所以放 Service，写回列表状态由各自的 ViewModel 做。
/// ITAD only offers candidates, a game may have several `app/` entries and the first may be gone, so several candidates
/// are checked with Steam; this depends only on the two APIs, not on page state, so it lives in a service and each
/// view model writes the result back into its own list.
enum SteamAppIDResolver {

    /// 批量查出每个游戏的 Steam appID。
    /// Looks up the Steam appID of every game in the batch.
    ///
    /// 只把多候选游戏的 appID 拿去 Steam 校验，一个都没有就不发这次请求；校验失败不抛错，退回第一个候选。
    /// Only games with several candidates go to Steam, and no request is sent when there are none;
    /// a failed check does not throw, it falls back to the first candidate.
    ///
    /// - Parameters:
    ///   - uuids: 要查的 ITAD 游戏 UUID / The ITAD game UUIDs to resolve.
    ///   - region: 商店区域，校验多个候选时用 / The store region, used when checking several candidates.
    /// - Returns: ITAD UUID → appID；不在字典里就是查了没有 / ITAD UUID to appID; a missing game has none.
    /// - Throws: ITAD lookup 请求失败时抛出原错误 / The original error when the ITAD lookup request fails.
    static func resolveAppIDs(uuids: [String], region: AppRegion) async throws -> [String: Int] {
        guard !uuids.isEmpty else { return [:] }

        let lookup = try await ITADAPI.lookupShopIDs(uuids: uuids)

        // 1. 每个游戏的候选 appID，可能 0 个、1 个或多个 / Each game's candidate appIDs: zero, one or several
        var candidatesByUUID: [String: [Int]] = [:]
        for uuid in uuids {
            candidatesByUUID[uuid] = lookup.appIDs(for: uuid)
        }

        // 2. 只把多候选游戏的 appID 拿去 Steam 校验 / Only appIDs from games with several candidates go to Steam
        var appIDsToCheck: [Int] = []
        for candidates in candidatesByUUID.values where candidates.count > 1 {
            appIDsToCheck.append(contentsOf: candidates)
        }

        var availability: SteamAppAvailabilityResponse?
        if !appIDsToCheck.isEmpty {
            availability = await fetchAvailability(of: appIDsToCheck, region: region)
        }

        // 3. 每个游戏定一个 appID，选不出的不放进字典 / Pick one appID per game; games without one are left out
        var appIDByUUID: [String: Int] = [:]
        for (uuid, candidates) in candidatesByUUID {
            appIDByUUID[uuid] = pickAppID(from: candidates, availability: availability)
        }

        #if DEBUG
        Logger.itad.debug("Steam appID 解析 / resolve: \(lookup.trackedCount)/\(uuids.count) 条拿到 appID")
        #endif

        return appIDByUUID
    }

    // MARK: - 私有 / Private

    /// 向 Steam 校验候选 appID，请求失败返回 `nil`。
    /// Checks candidate appIDs with Steam, returning `nil` when the request fails.
    ///
    /// 失败不往外抛：校验只是为了在多个候选里挑对的，挑不了就退回第一个候选，和没校验前的行为一样。
    /// A failure is not thrown: the check only helps pick among candidates, and without it the first candidate is used,
    /// the same as before the check existed.
    ///
    /// - Parameters:
    ///   - appIDs: 要校验的 appID / The appIDs to check.
    ///   - region: 商店区域 / The store region.
    /// - Returns: Steam 的校验结果，失败是 `nil` / Steam's result, or `nil` on failure.
    private static func fetchAvailability(of appIDs: [Int], region: AppRegion) async -> SteamAppAvailabilityResponse? {
        do {
            return try await SteamAPI.fetchAvailability(of: appIDs, region: region)
        } catch {
            if !Task.isCancelled {
                Logger.steam.error("appID 校验失败，退回第一个候选 / check failed, using first candidate: \(String(describing: error))")
            }
            return nil
        }
    }

    /// 从候选里挑一个 appID，`nil` 表示选不出。
    /// Picks one appID from the candidates, or `nil` when none fits.
    ///
    /// 先挑有价格的：调用方都是在打折的 deal 列表，对的那个一定有价格；只看「存在」会选到 demo（FORM）或无价格的旧页（GTA IV 的 901583）。
    /// The first priced one wins: every caller lists deals on sale, so the right one always has a price; checking only
    /// existence picks a demo (FORM) or an old unpriced page (GTA IV's 901583).
    ///
    /// - Parameters:
    ///   - candidates: 这个游戏的全部候选 / All of this game's candidates.
    ///   - availability: Steam 校验结果，没校验或校验失败是 `nil` / Steam's check, `nil` when unchecked or failed.
    /// - Returns: 选中的 appID，选不出是 `nil` / The picked appID, or `nil` when none fits.
    private static func pickAppID(from candidates: [Int], availability: SteamAppAvailabilityResponse?) -> Int? {
        // 没有候选：ITAD 没收录，或只有 `sub/` / No candidates: untracked, or only `sub/` entries
        if candidates.isEmpty {
            return nil
        }

        // 只有一个：直接用，它没被拿去 Steam 校验 / A single candidate is used as-is; it was never sent to Steam
        if candidates.count == 1 {
            return candidates[0]
        }

        // 多个但校验失败：退回第一个 / Several but the check failed: the first one
        guard let availability else {
            return candidates[0]
        }

        // 多个：先取第一个有价格的 / Several: the first priced one
        for appID in candidates where availability.hasPrice(appID) {
            return appID
        }

        // 都没价格：取第一个还存在的 / None priced: the first one that still exists
        for appID in candidates where availability.isAvailable(appID) {
            return appID
        }

        // 都不存在：点进去也只会报错 / None exists: the detail page would only fail
        return nil
    }
}
