//
//  GamesViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation
import OSLog

@MainActor
@Observable
final class GamesViewModel {

    /// Deals 列表状态机 / State machine for the deal list
    enum DealLoadState: Equatable {
        /// 首次加载或重试中 / First load, or a retry in progress
        case loading
        /// 请求成功，列表可能为空；装 `HomeDealItem` 是因为渲染只要「deal + appID」/
        /// Request succeeded, the list may still be empty; carries `HomeDealItem` because rendering
        /// only needs deal plus appID
        case loaded([HomeDealItem])
        /// 请求失败，携带归一化后的错误 / Request failed, carrying the normalised error
        case failed(LoadError)
    }

    /// Giveaways 区块的独立状态机：与 deals 互不影响 /
    /// Independent state machine for the giveaways section; it never affects the deals state
    enum GiveawaysLoadState: Equatable {
        /// 首次加载或重试中 / First load, or a retry in progress
        case loading
        /// 请求成功，列表可能为空 / Request succeeded; the list itself may still be empty
        case loaded(ITADGiveawaysResponse)
        /// 请求失败，携带归一化后的错误 / Request failed, carrying the normalised error
        case failed(LoadError)
    }

    /// Deals 列表当前状态 / Current state of the deal list
    private(set) var dealState: DealLoadState = .loading

    /// Giveaways 区块当前状态 / Current state of the giveaways section
    private(set) var giveawaysState: GiveawaysLoadState = .loading

    /// 请求体，country 跟随传入的区域 / Request body; country follows the region passed in
    /// - Parameter region: 商店区域，由界面从设置读出后传入 / The store region, read from settings by the view
    private func requestBody(region: AppRegion) -> [String: Any] {
        [
            "country": region.apiRegionCode,
            "offset": 0,
            "limit": 20,
           "sort": "-hot",
            "nondeals": false,
            "filter": [
                "type": [1],
                "drm": [61],
            ],
        ]
    }

    /// 界面传入区域后加载两个区块 / Loads both sections with the region handed in by the view
    func load(region: AppRegion) async {
        async let deals: Void = fetchDeals(region: region)
        async let giveaways: Void = fetchGiveaways()
        _ = await (deals, giveaways)
    }

    /// 下拉刷新：旧内容留到新数据回来，appID 也一起重查 / Pull to refresh: keeps the old content until new data lands, re-resolving appIDs too
    /// - Parameter region: 商店区域 / The store region
    func refresh(region: AppRegion) async {
        async let deals: Void = fetchDeals(region: region, showsLoading: false)
        async let giveaways: Void = fetchGiveaways(showsLoading: false)
        _ = await (deals, giveaways)
    }

    /// 拉取优惠列表 / Fetches the deal list
    /// - Parameter region: 商店区域 / The store region
    /// - Parameter showsLoading: 是否先切到加载态；下拉刷新时系统已有转圈，不切 / Whether to switch to loading first; pull to refresh already shows a spinner
    func fetchDeals(region: AppRegion, showsLoading: Bool = true) async {
        if showsLoading { dealState = .loading }

        do {
            let data = try await ITADAPI.fetchDeals(body: requestBody(region: region))
            // 先发布状态让卡片立刻渲染，appID 仍是 `.pending` / Publish first so the cards render; the appID is still `.pending`
            dealState = .loaded(data.list.map { HomeDealItem(deal: $0) })

            // 预取在渲染之后，不挡首屏 / Prefetch runs after publishing so it never delays the first frame
            await prefetchAppIDs(region: region)

        } catch {
            // 取消（视图离开或任务重跑）不算失败 / A cancellation (view left, or the task restarted) is not a failure
            if Task.isCancelled { return }

            // 完整错误只进日志，界面只看到 `LoadError` / Log the full error; the UI only sees `LoadError`
            Logger.games.error("fetchDeals failed / failed: \(String(describing: error))")

            dealState = .failed(LoadError.from(error))
        }
    }

    /// 重试 deals，只影响第一个区块 / Retries deals; only the first section is affected
    /// - Parameter region: 商店区域 / The store region
    func retryDeals(region: AppRegion) async {
        await fetchDeals(region: region)
    }

    // MARK: - Steam appID 预取 / Prefetching Steam appIDs

    /// 给列表里每个游戏批量补上 Steam appID（实测 60 个 UUID 一次请求即可）/
    /// Resolves the Steam appID for every game in the feed, in one batch
    ///
    /// 失败不改 `dealState`：列表是好的，只是进不了详情页 / A failure leaves `dealState` alone
    /// - Parameter region: 商店区域，校验多个候选时用 / The store region, used when checking multiple candidates
    func prefetchAppIDs(region: AppRegion) async {
        guard case .loaded(let items) = dealState else { return }
        guard !items.isEmpty else { return }

        let uuids = items.map(\.id)

        do {
            let lookup = try await ITADAPI.lookupShopIDs(uuids: uuids)

            // 请求在飞时可能换了地区或重试，结果已过期 / The region may have changed while this was in flight
            guard !Task.isCancelled else { return }

            // 1. 每个游戏的候选 appID，可能 0 个、1 个或多个 / Each game's candidate appIDs: zero, one or several
            var candidatesByUUID: [String: [Int]] = [:]
            for uuid in uuids {
                candidatesByUUID[uuid] = lookup.appIDs(for: uuid)
            }

            // 2. 只把多候选游戏的 appID 拿去 Steam 校验；一个都没有就不发请求 /
            //    Only appIDs from games with several candidates go to Steam; no request when there are none
            var appIDsToCheck: [Int] = []
            for candidates in candidatesByUUID.values where candidates.count > 1 {
                appIDsToCheck.append(contentsOf: candidates)
            }

            var availability: SteamAppAvailabilityResponse?
            if !appIDsToCheck.isEmpty {
                availability = await fetchAvailability(of: appIDsToCheck, region: region)
                guard !Task.isCancelled else { return }
            }

            // 3. 每个游戏定一个 appID，选不出的不放进字典 / Pick one appID per game; games without one are left out
            var appIDByUUID: [String: Int] = [:]
            for (uuid, candidates) in candidatesByUUID {
                appIDByUUID[uuid] = Self.pickAppID(from: candidates, availability: availability)
            }

            // 4. 整批一次写回，避免 N 次数组复制 / Apply the whole batch in one pass
            applyAppIDs(appIDByUUID)

            #if DEBUG
            Logger.games.debug("Steam appID 预取 / prefetch: \(lookup.trackedCount)/\(uuids.count) 条拿到 appID")
            #endif

        } catch {
            if Task.isCancelled { return }

            Logger.games.error("prefetchAppIDs failed / failed: \(String(describing: error))")

            // 还挂在 `.pending` 的条目标成 `.unavailable`，否则卡片永远点不动 / Settle anything still `.pending`
            settlePendingAsUnavailable()
        }
    }

    /// 向 Steam 校验候选 appID；请求失败返回 `nil`，调用方退回旧做法 /
    /// Checks candidate appIDs with Steam; `nil` on failure so the caller falls back to the old behaviour
    private func fetchAvailability(of appIDs: [Int], region: AppRegion) async -> SteamAppAvailabilityResponse? {
        do {
            return try await SteamAPI.fetchAvailability(of: appIDs, region: region)
        } catch {
            if !Task.isCancelled {
                Logger.games.error("appID 校验失败，退回第一个候选 / check failed, using first candidate: \(String(describing: error))")
            }
            return nil
        }
    }

    /// 从候选里挑一个 appID，`nil` 表示选不出 / Picks one appID from the candidates; `nil` when none fits
    /// - Parameter candidates: 这个游戏的全部候选 / All of this game's candidates
    /// - Parameter availability: Steam 校验结果；没校验或校验失败是 `nil` / Steam's check; `nil` when unchecked or failed
    private static func pickAppID(from candidates: [Int], availability: SteamAppAvailabilityResponse?) -> Int? {
        // 没有候选：ITAD 没收录，或只有 `sub/` / No candidates: untracked, or only `sub/` entries
        if candidates.isEmpty {
            return nil
        }

        // 只有一个：直接用，它没被拿去 Steam 校验 / A single candidate is used as-is; it was never sent to Steam
        if candidates.count == 1 {
            return candidates[0]
        }

        // 多个但校验失败：退回第一个，和校验前的行为一样 / Several but the check failed: the first, as before
        guard let availability else {
            return candidates[0]
        }

        // 多个：先取第一个有价格的。首页都是在打折的 deal，对的那个一定有价格；只看「存在」会选到 demo（FORM）或无价格的旧页（GTA IV 的 901583）/
        // Several: the first priced one wins. Every home deal is on sale, so the right one always has a price
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

    /// 按选好的 appID 整批更新列表 / Applies the picked appIDs across the whole feed in one pass
    ///
    /// - Parameter appIDByUUID: ITAD UUID → appID；不在字典里的游戏标成 `.unavailable` /
    ///   ITAD UUID to appID; games missing from it become `.unavailable`
    private func applyAppIDs(_ appIDByUUID: [String: Int]) {
        guard case .loaded(let items) = dealState else { return }

        dealState = .loaded(items.map { item in
            var updated = item

            if let appID = appIDByUUID[item.id] {
                updated.steam = .resolved(appID)
            } else {
                updated.steam = .unavailable
            }

            return updated
        })
    }

    /// 把仍在等待中的条目落定为 `.unavailable` /
    /// Settles every still-pending entry as `.unavailable`
    private func settlePendingAsUnavailable() {
        guard case .loaded(let items) = dealState else { return }

        dealState = .loaded(items.map { item in
            guard item.steam == .pending else { return item }
            var updated = item
            updated.steam = .unavailable
            return updated
        })
    }

    /// 拉取限时免费领取列表 / Fetches the giveaways list
    /// - Parameter showsLoading: 是否先切到加载态；下拉刷新时不切 / Whether to switch to loading first; not during pull to refresh
    func fetchGiveaways(showsLoading: Bool = true) async {
        if showsLoading { giveawaysState = .loading }

        do {
            let giveaways = try await ITADAPI.fetchGiveaways()
            giveawaysState = .loaded(giveaways)

        } catch {
            // 取消（视图离开或任务重跑）不算失败 / A cancellation (view left, or the task restarted) is not a failure
            if Task.isCancelled { return }

            // 完整错误只进日志，界面只看到 `LoadError` / Log the full error; the UI only sees `LoadError`
            Logger.games.error("fetchGiveaways failed / failed: \(String(describing: error))")

            giveawaysState = .failed(LoadError.from(error))
        }
    }

    /// 重试 giveaways，只影响第二个区块 / Retries giveaways; only the second section is affected
    func retryGiveaways() async {
        await fetchGiveaways()
    }
}
