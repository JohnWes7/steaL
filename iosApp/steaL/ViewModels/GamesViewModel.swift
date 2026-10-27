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
            // 首页只放 5 条：1 张大卡 + 2 行网格，下面的 Giveaways 不被挤到很远 /
            // The home feed shows 5: one featured card plus two grid rows, keeping Giveaways close
            "limit": 5,
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

    /// 给列表里每个游戏批量补上 Steam appID / Resolves the Steam appID for every game in the feed, in one batch
    ///
    /// 失败不改 `dealState`：列表是好的，只是进不了详情页 / A failure leaves `dealState` alone
    /// - Parameter region: 商店区域，校验多个候选时用 / The store region, used when checking multiple candidates
    func prefetchAppIDs(region: AppRegion) async {
        guard case .loaded(let items) = dealState else { return }
        guard !items.isEmpty else { return }

        let uuids = items.map(\.id)

        do {
            let appIDByUUID = try await SteamAppIDResolver.resolveAppIDs(uuids: uuids, region: region)

            // 请求在飞时可能换了地区或重试，结果已过期 / The region may have changed while this was in flight
            guard !Task.isCancelled else { return }

            applyAppIDs(appIDByUUID)

        } catch {
            if Task.isCancelled { return }

            Logger.games.error("prefetchAppIDs failed / failed: \(String(describing: error))")

            // 还挂在 `.pending` 的条目标成 `.failed`，否则卡片永远点不动 / Settle anything still `.pending` as failed
            settlePendingAsFailed()
        }
    }

    /// 按选好的 appID 整批更新列表 / Applies the picked appIDs across the whole feed in one pass
    ///
    /// 只在 lookup 请求成功后调用，所以不在字典里就是真的没有；请求失败走 `settlePendingAsFailed()` /
    /// Only called once the lookup succeeded, so a missing game really has none; a failed request goes to `settlePendingAsFailed()`
    ///
    /// - Parameter appIDByUUID: ITAD UUID → appID；不在字典里的游戏标成 `.notFound` /
    ///   ITAD UUID to appID; games missing from it become `.notFound`
    private func applyAppIDs(_ appIDByUUID: [String: Int]) {
        guard case .loaded(let items) = dealState else { return }

        dealState = .loaded(items.map { item in
            var updated = item

            if let appID = appIDByUUID[item.id] {
                updated.steam = .resolved(appID)
            } else {
                updated.steam = .notFound
            }

            return updated
        })
    }

    /// lookup 请求失败时，把仍在等待中的条目落定为 `.failed` /
    /// Settles every still-pending entry as `.failed` once the lookup request has failed
    private func settlePendingAsFailed() {
        guard case .loaded(let items) = dealState else { return }

        dealState = .loaded(items.map { item in
            guard item.steam == .pending else { return item }
            var updated = item
            updated.steam = .failed
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
