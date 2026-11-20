//
//  GamesViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation
import OSLog

/// 首页的数据状态：Hot Deals 前 5 条与 Giveaways 两个区块，各自加载、失败、重试。
/// The home page's data state: the first five Hot Deals and the Giveaways, each loading, failing and retrying on its own.
///
/// 这里只负责「取数据并写进状态」（`update…`）；什么时候开始、什么时候取消由 `GamesView` 决定，它给每个区块存一个 `Task`。
/// This only fetches data and writes it into state (`update…`); `GamesView` decides when to start and cancel, keeping
/// one `Task` per section.
@MainActor
@Observable
final class GamesViewModel {

    /// Deals 列表的状态。
    /// The state of the deal list.
    enum DealLoadState: Equatable {
        /// 首次加载、换地区或重试中。
        /// The first load, a region change, or a retry in progress.
        case loading

        /// 请求成功，列表可能为空；每条带着它的 Steam 关联状态。
        /// The request succeeded and the list may be empty; each row carries its Steam link state.
        case loaded([HomeDealItem])

        /// 请求失败，带着归一后的原因。
        /// The request failed, carrying the normalised reason.
        case failed(LoadError)
    }

    /// Giveaways 区块的状态，和 deals 互不影响。
    /// The state of the giveaways section, independent of the deals.
    enum GiveawaysLoadState: Equatable {
        /// 首次加载或重试中。
        /// The first load, or a retry in progress.
        case loading

        /// 请求成功，列表可能为空。
        /// The request succeeded; the list may be empty.
        case loaded(ITADGiveawaysResponse)

        /// 请求失败，带着归一后的原因。
        /// The request failed, carrying the normalised reason.
        case failed(LoadError)
    }

    /// Deals 列表当前的状态。
    /// The deal list's current state.
    private(set) var dealState: DealLoadState = .loading

    /// Giveaways 区块当前的状态。
    /// The giveaways section's current state.
    private(set) var giveawaysState: GiveawaysLoadState = .loading

    // MARK: - Deals

    /// 取首页的 deals 写进 `dealState`，再补 Steam appID。
    /// Fetches the home deals into `dealState`, then resolves their Steam appIDs.
    ///
    /// 首次加载、换地区、重试、下拉刷新都调它，区别只在 `showsLoading`（同 Apple 示例里 `.task` 和 `.refreshable` 调同一个 `updateItems()`）。
    /// 被取消就是有新请求接手了（换地区、刷新、重试），旧结果不写。
    /// The first load, a region change, a retry and pull to refresh all call this, differing only in `showsLoading`
    /// (as `.task` and `.refreshable` call the same `updateItems()` in Apple's sample). A cancellation means a newer
    /// request took over (a region change, refresh or retry), so the stale result is not written.
    ///
    /// - Parameters:
    ///   - region: 商店区域，由界面从设置读出后传入 / The store region, read from settings by the view.
    ///   - showsLoading: 是否先切到加载态；下拉刷新传 `false`，旧列表留到新数据回来 / Whether to switch to loading first; pull to refresh passes `false` so the old list stays until new data arrives.
    func updateDeals(region: AppRegion, showsLoading: Bool) async {
        if showsLoading {
            dealState = .loading
        }

        let response: ITADDealsResponse
        do {
            response = try await ITADAPI.fetchDeals(body: requestBody(region: region))
        } catch {
            // 取消（被新请求接手）不算失败。
            // A cancellation (a newer request took over) is not a failure.
            if Task.isCancelled { return }

            // 完整错误只进日志，界面只看到 `LoadError`。
            // The full error goes to the log only; the UI sees just a `LoadError`.
            Logger.games.error("deals 加载失败 / Loading deals failed: \(String(describing: error))")
            dealState = .failed(LoadError.from(error))
            return
        }

        // 请求已经回来、等着回主线程时被取消，`await` 照样带着结果返回，所以写之前再查一次。
        // A task cancelled while its finished request waits to resume still returns the result, so check before writing.
        guard !Task.isCancelled else { return }

        // 先发布，卡片马上显示，appID 还是 `.pending`；补 appID 不挡首屏。
        // Publish first so the cards show right away with appIDs still `.pending`; resolving never delays them.
        dealState = .loaded(response.list.map { deal in HomeDealItem(deal: deal) })

        await resolveAppIDs(region: region)
    }

    /// 首页 deals 的请求体：这个地区的 5 条热门优惠，只要游戏、只要 Steam 激活的。
    /// The home deals request body: five hot deals for the region, games only, Steam activation only.
    ///
    /// - Parameter region: 商店区域，`country` 跟着它 / The store region that `country` follows.
    /// - Returns: `deals/v2` 的 JSON 请求体 / The JSON body for `deals/v2`.
    private func requestBody(region: AppRegion) -> [String: Any] {
        [
            "country": region.apiRegionCode,
            "offset": 0,
            // 首页只放 5 条：1 张大卡 + 2 行网格，下面的 Giveaways 不被挤到很远。
            // The home page shows five: one featured card plus two grid rows, keeping Giveaways close.
            "limit": 5,
            "sort": "-hot",
            "nondeals": false,
            "filter": [
                "type": [1],
                "drm": [61],
            ],
        ]
    }

    // MARK: - Steam appID

    /// 给列表里每个游戏批量补上 Steam appID。
    /// Resolves the Steam appID for every game in the list, in one batch.
    ///
    /// 失败不改列表本身：列表是好的，只是进不了详情页，所以只把还在等的条目标成 `.failed`。
    /// A failure leaves the list itself alone: it is fine, the rows just cannot open the detail page, so only the
    /// rows still waiting become `.failed`.
    ///
    /// - Parameter region: 商店区域，校验多个候选时用 / The store region, used when checking multiple candidates.
    private func resolveAppIDs(region: AppRegion) async {
        guard case .loaded(let items) = dealState else { return }
        guard !items.isEmpty else { return }

        let uuids = items.map(\.itadUUID)

        do {
            let appIDByUUID = try await SteamAppIDResolver.resolveAppIDs(uuids: uuids, region: region)

            // 查询期间可能被新请求接手，结果已经过期。
            // A newer request may have taken over during the lookup, making this result stale.
            guard !Task.isCancelled else { return }

            applyAppIDs(appIDByUUID)

        } catch {
            if Task.isCancelled { return }

            Logger.games.error("Steam appID 补全失败 / Resolving Steam appIDs failed: \(String(describing: error))")

            // 还在等的条目标成 `.failed`，否则卡片永远点不动。
            // Mark the rows still waiting as `.failed`, or those cards never respond.
            settlePendingAsFailed()
        }
    }

    /// 按查到的 appID 整批更新列表，不在字典里的标成 `.notFound`。
    /// Updates the whole list with the resolved appIDs; rows missing from the dictionary become `.notFound`.
    ///
    /// 只在查询成功后调用，所以不在字典里就是真的没有；请求失败走 `settlePendingAsFailed()`。
    /// Only called after a successful lookup, so a missing row really has none; a failed request goes to
    /// `settlePendingAsFailed()`.
    ///
    /// - Parameter appIDByUUID: ITAD UUID → appID / ITAD UUID to appID.
    private func applyAppIDs(_ appIDByUUID: [String: Int]) {
        guard case .loaded(let items) = dealState else { return }

        dealState = .loaded(items.map { item in
            var updated = item

            if let appID = appIDByUUID[item.itadUUID] {
                updated.steam = .resolved(appID)
            } else {
                updated.steam = .notFound
            }

            return updated
        })
    }

    /// 查询请求失败后，把还在等的条目标成 `.failed`。
    /// After the lookup request fails, marks the rows still waiting as `.failed`.
    private func settlePendingAsFailed() {
        guard case .loaded(let items) = dealState else { return }

        dealState = .loaded(items.map { item in
            guard item.steam == .pending else { return item }
            var updated = item
            updated.steam = .failed
            return updated
        })
    }

    // MARK: - Giveaways

    /// 取限时免费领取列表写进 `giveawaysState`。
    /// Fetches the giveaways list into `giveawaysState`.
    ///
    /// - Parameter showsLoading: 是否先切到加载态；下拉刷新传 `false` / Whether to switch to loading first; pull to refresh passes `false`.
    func updateGiveaways(showsLoading: Bool) async {
        if showsLoading {
            giveawaysState = .loading
        }

        let giveaways: ITADGiveawaysResponse
        do {
            giveaways = try await ITADAPI.fetchGiveaways()
        } catch {
            // 取消（被新请求接手）不算失败。
            // A cancellation (a newer request took over) is not a failure.
            if Task.isCancelled { return }

            Logger.games.error("giveaways 加载失败 / Loading giveaways failed: \(String(describing: error))")
            giveawaysState = .failed(LoadError.from(error))
            return
        }

        // 同 `updateDeals`：请求回来后被取消也会带着结果返回，写之前再查一次。
        // As in `updateDeals`: a cancelled task still returns the finished result, so check before writing.
        guard !Task.isCancelled else { return }

        giveawaysState = .loaded(giveaways)
    }
}
