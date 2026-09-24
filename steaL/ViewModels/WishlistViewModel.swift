//
//  WishlistViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/22.
//

import Foundation

/// Wishlist 页的价格与发售状态：收藏本身由 `WishlistService` 推送，这里按 appID 管每行的价格和 Steam 核对结果 /
/// Prices and release status for the wishlist page: the saved items come from `WishlistService`, this tracks each row's price and Steam check by appID
///
/// 发售状态核对后与 Firestore 不同的值由方法返回，`WishlistView` 交给 `WishlistService` 回写（VM 不持有 Service）/
/// Release statuses that differ from Firestore are returned, and `WishlistView` writes them back through `WishlistService` (the VM holds no service)
///
/// 价格只存在内存里，不过期：新增收藏、换区域、下拉刷新时才查 /
/// Prices live in memory and never expire; they're fetched for new items, a region change or pull to refresh
@MainActor
@Observable
final class WishlistViewModel {

    // MARK: - 状态 / State

    /// 一行的价格状态 / One row's price state
    enum PriceState {
        /// 还没查到 / Not fetched yet
        case loading
        /// 能在 Steam 激活的 deal 里最便宜的那条 / The cheapest deal that activates on Steam
        case loaded(ITADPriceDeal)
        /// ITAD 没收录，或没有能在 Steam 激活的价格 / ITAD doesn't track it, or no deal activates on Steam
        case noPrice
        /// lookup 或价格请求失败，下拉刷新重试 / The lookup or price request failed; pull to refresh retries
        case failed

        /// 是否还在等结果 / Whether the result is still pending
        var isLoading: Bool {
            if case .loading = self {
                return true
            }
            return false
        }
    }

    /// 每行的价格，按 appID；没有的行按 `.loading` 处理 / Each row's price by appID; a missing row reads as `.loading`
    private(set) var priceStates: [Int: PriceState] = [:]

    /// 上次加载用的区域，变了就清空价格（币种不同）/ The region of the last load; a change clears the prices (different currency)
    private var loadedRegion: AppRegion?

    /// 本次运行 lookup 到的 UUID，按 appID；和区域无关，换区域不清 /
    /// UUIDs looked up during this run, by appID; region-independent, so a region change keeps them
    private var lookedUpUUIDsCache: [Int: String] = [:]

    /// `prices/v3` 一次最多 200 个 UUID / `prices/v3` takes at most 200 UUIDs per request
    private static let batchLimit = 200

    /// 某一行现在的价格状态 / A row's current price state
    func priceState(for item: WishlistItem) -> PriceState {
        let state = priceStates[item.appID]
        return state ?? .loading
    }

    // MARK: - 筛选 / Filtering

    /// 按筛选项挑出要显示的行，顺序不变 / The rows to show for a filter, in their original order
    /// - Parameter items: 当前的收藏 / The current wishlist
    /// - Parameter filter: 顶部选中的筛选项 / The selected filter
    func items(_ items: [WishlistItem], matching filter: WishlistFilter) -> [WishlistItem] {
        items.filter { item in matches(item, filter: filter) }
    }

    /// 这个筛选要的数据还有没有行在等；筛选结果为空时用来区分「还在查」和「真的没有」/
    /// Whether any row is still waiting for the data this filter needs; tells "still loading" from "none" when a filter comes up empty
    func hasPendingResults(in items: [WishlistItem], for filter: WishlistFilter) -> Bool {
        switch filter {
        case .all:
            return false
        case .onSale, .allTimeLow:
            return items.contains { item in priceState(for: item).isLoading }
        case .upcoming:
            return items.contains { item in isReleasePending(item) }
        }
    }

    /// 一行是否符合筛选；价格没查到（加载中、没价格、失败）的行只在 All 里出现 /
    /// Whether a row passes a filter; rows without a loaded price only show under All
    private func matches(_ item: WishlistItem, filter: WishlistFilter) -> Bool {
        let deal = loadedDeal(for: item)
        switch filter {
        case .all:
            return true
        case .onSale:
            guard let deal else { return false }
            return deal.cut > 0
        case .allTimeLow:
            guard let deal else { return false }
            // ITAD 的 H / N 标记，和首页「ALL-TIME LOW」标签同一个判断 / ITAD's H / N flag, the same test as the home page's "ALL-TIME LOW" tag
            return deal.isHistoricalLow
        case .upcoming:
            return isComingSoon(item)
        }
    }

    /// 已查到的 deal，其余状态为 nil / The loaded deal; nil for every other state
    private func loadedDeal(for item: WishlistItem) -> ITADPriceDeal? {
        guard case .loaded(let deal) = priceState(for: item) else {
            return nil
        }
        return deal
    }

    // MARK: - 发售状态 / Release status

    /// 向 Steam 核对一行发售状态的结果 / The outcome of checking one row's release status with Steam
    enum ReleaseCheck {
        case comingSoon
        case released
        /// 请求失败或 Steam 查不到，沿用 Firestore 存的值 / The request failed or Steam has no entry; the stored value stands
        case failed

        /// 核对出的结果，失败为 nil / The checked result; nil on failure
        var isComingSoon: Bool? {
            switch self {
            case .comingSoon: return true
            case .released: return false
            case .failed: return nil
            }
        }
    }

    /// 本次运行核对过的发售状态，按 appID；存 `true` 的行每次运行核对一次，发售了才能发现 /
    /// Release statuses checked during this run, by appID; rows stored as `true` are checked once per run so a release gets noticed
    private(set) var releaseChecksState: [Int: ReleaseCheck] = [:]

    /// 一行是否未发售：先看本次核对的结果，没有就用 Firestore 存的值，都没有当作已发售 /
    /// Whether a row is unreleased: this run's check first, then the stored value, and released when neither is known
    func isComingSoon(_ item: WishlistItem) -> Bool {
        let check = releaseChecksState[item.appID]
        if let checked = check?.isComingSoon {
            return checked
        }
        let stored = item.isComingSoon
        return stored ?? false
    }

    /// 还不知道是否未发售（存的是 nil 且还没核对完）/ Release status still unknown (stored as nil and not checked yet)
    private func isReleasePending(_ item: WishlistItem) -> Bool {
        if releaseChecksState[item.appID] != nil {
            return false
        }
        return item.isComingSoon == nil
    }

    /// 进页面或收藏变化时调用：核对存的是 `true` 或 nil、本次还没核对过的行 /
    /// Called on appear or when the wishlist changes: checks rows stored as `true` or nil that haven't been checked this run
    /// - Returns: 和 Firestore 不同、要回写的值，按 appID / Values that differ from Firestore and should be written back, by appID
    func checkReleases(for items: [WishlistItem], region: AppRegion) async -> [Int: Bool] {
        // 检查之前有没有查过
        let uncheckedItems = items.filter { item in releaseChecksState[item.appID] == nil }
        let itemsToCheck = uncheckedItems.filter { item in item.isComingSoon != false }
        return await fetchReleaseChecks(for: itemsToCheck, region: region)
    }

    /// 下拉刷新：重新核对所有存的不是 `false` 的行 / Pull to refresh: rechecks every row not stored as `false`
    /// - Returns: 和 Firestore 不同、要回写的值，按 appID / Values that differ from Firestore and should be written back, by appID
    func refreshReleases(for items: [WishlistItem], region: AppRegion) async -> [Int: Bool] {
        let itemsToCheck = items.filter { item in item.isComingSoon != false }
        return await fetchReleaseChecks(for: itemsToCheck, region: region)
    }

    /// 并行请求 Steam 详情，整批写回 `releaseChecksState`，返回要回写 Firestore 的差异 /
    /// Fetches Steam details in parallel, writes `releaseChecksState` once and returns the differences to write back to Firestore
    ///
    /// Steam `appdetails` 多个 appid 只支持 `filters=price_overview`，没法批量查发售状态 /
    /// Steam's `appdetails` only takes several appids with `filters=price_overview`, so release status can't be batched
    private func fetchReleaseChecks(for items: [WishlistItem], region: AppRegion) async -> [Int: Bool] {
        guard !items.isEmpty else { return [:] }

        let appIDs = items.map(\.appID)
        let results = await withTaskGroup(of: (Int, ReleaseCheck).self) { group in
            for appID in appIDs {
                group.addTask {
                    let check = await Self.checkRelease(appID: appID, region: region)
                    return (appID, check)
                }
            }

            var results: [Int: ReleaseCheck] = [:]
            for await (appID, check) in group {
                results[appID] = check
            }
            return results
        }
        guard !Task.isCancelled else { return [:] }

        var updatedChecks = releaseChecksState
        var corrections: [Int: Bool] = [:]
        for item in items {
            guard let check = results[item.appID] else { continue }
            updatedChecks[item.appID] = check

            // 失败不回写，Firestore 保持原值 / A failure writes nothing; Firestore keeps its value
            guard let checked = check.isComingSoon else { continue }
            if checked != item.isComingSoon {
                corrections[item.appID] = checked
            }
        }
        releaseChecksState = updatedChecks
        return corrections
    }

    /// 向 Steam 核对一个游戏是否未发售 / Checks with Steam whether one game is unreleased
    private static func checkRelease(appID: Int, region: AppRegion) async -> ReleaseCheck {
        do {
            let details = try await SteamAPI.fetchAppDetails(appID: appID, region: region)
            if details.isComingSoon {
                return .comingSoon
            }
            return .released
        } catch {
            if !Task.isCancelled {
                print("[WishlistViewModel] release check failed / failed: \(error)")
            }
            return .failed
        }
    }

    // MARK: - 加载 / Loading

    /// 进页面或收藏变化时调用：只查还没有状态的行，删一条不会重查其他行 /
    /// Called on appear or when the wishlist changes: only rows without a state are fetched, so a removal refetches nothing
    /// - Parameter items: 当前的收藏 / The current wishlist
    /// - Parameter region: 商店区域 / The store region
    func loadPrices(for items: [WishlistItem], region: AppRegion) async {
        if loadedRegion != region {
            priceStates = [:]
            loadedRegion = region
        }

        // 丢掉已移除的行，重新加回来时会再查一次 / Drop removed rows so a re-added game is fetched again
        let currentAppIDs = Set(items.map(\.appID))
        priceStates = priceStates.filter { entry in currentAppIDs.contains(entry.key) }

        let newItems = items.filter { item in priceStates[item.appID] == nil }
        await fetchPrices(for: newItems, region: region)
    }

    /// 下拉刷新：重查全部行，旧价格留到新结果回来 / Pull to refresh: refetches every row, keeping old prices until new ones land
    /// - Parameter items: 当前的收藏 / The current wishlist
    /// - Parameter region: 商店区域 / The store region
    func refresh(items: [WishlistItem], region: AppRegion) async {
        await fetchPrices(for: items, region: region)
    }

    /// 解析 UUID、批量查价，最后整批写回 `priceStates` / Resolves UUIDs, fetches prices in a batch, then writes `priceStates` once
    private func fetchPrices(for items: [WishlistItem], region: AppRegion) async {
        guard !items.isEmpty else { return }

        // 1. 已知 UUID 的直接用，其余要 lookup / Use known UUIDs; the rest need a lookup
        var uuidByAppID: [Int: String] = [:]
        var appIDsToLookUp: [Int] = []
        for item in items {
            let cachedUUID = lookedUpUUIDsCache[item.appID]
            let knownUUID = item.itadID ?? cachedUUID
            if let knownUUID {
                uuidByAppID[item.appID] = knownUUID
            } else {
                appIDsToLookUp.append(item.appID)
            }
        }

        // 2. 并行 lookup；查不到或失败的行直接定下状态 / Parallel lookups; misses and failures settle their rows right away
        var statesFromLookup: [Int: PriceState] = [:]
        let lookupResults = await lookUpUUIDs(for: appIDsToLookUp)
        guard isCurrent(region) else { return }

        for (appID, result) in lookupResults {
            switch result {
            case .found(let uuid):
                lookedUpUUIDsCache[appID] = uuid // into cache
                uuidByAppID[appID] = uuid
            case .notFound:
                statesFromLookup[appID] = .noPrice
            case .failed:
                statesFromLookup[appID] = .failed
            }
        }

        // 3. 有 UUID 的批量查价 / Batch-fetch prices for rows with a UUID
        let statesFromPrices = await fetchPriceStates(uuidByAppID: uuidByAppID, region: region)
        guard isCurrent(region) else { return }

        // 4. 整批写回，只触发一次重绘 / Write back in one go so the view redraws once
        var updatedPrices = priceStates
        for (appID, state) in statesFromLookup {
            updatedPrices[appID] = state
        }
        for (appID, state) in statesFromPrices {
            updatedPrices[appID] = state
        }
        priceStates = updatedPrices
    }

    /// 结果还该不该写：Task 被取消（切 tab、列表变化）或区域已换就丢掉 /
    /// Whether a result may still be written: dropped if the task was cancelled or the region changed meanwhile
    private func isCurrent(_ region: AppRegion) -> Bool {
        if Task.isCancelled {
            return false
        }
        return region == loadedRegion
    }

    // MARK: - 价格 / Prices

    /// 查价并落成每行的状态；请求失败时这一批全部 `.failed` /
    /// Fetches prices as per-row states; a failed request marks the whole batch `.failed`
    ///
    /// `prices/v3` 没有 DRM 筛选，`shops` 又会排除第三方 Steam key，所以拿全部商店、客户端取 `bestSteamDeal` /
    /// `prices/v3` has no DRM filter and `shops` would drop third-party Steam keys, so fetch every shop and pick `bestSteamDeal` locally
    private func fetchPriceStates(uuidByAppID: [Int: String], region: AppRegion) async -> [Int: PriceState] {
        guard !uuidByAppID.isEmpty else { return [:] }

        // 两个 appID 可能对应同一个 UUID，去重再请求 / Two appIDs can share a UUID, so dedupe before requesting
        let uuids = Array(Set(uuidByAppID.values))

        var games: ITADPricesResponse = []
        do {
            for start in stride(from: 0, to: uuids.count, by: Self.batchLimit) {
                let end = min(start + Self.batchLimit, uuids.count)
                let batch = Array(uuids[start..<end])
                let response = try await ITADAPI.fetchPrices(uuids: batch, region: region)
                games.append(contentsOf: response)
            }
        } catch {
            // 取消是正常的（切 tab），调用方会丢掉结果 / Cancellation is normal; the caller discards the result
            if !Task.isCancelled {
                print("[WishlistViewModel] prices failed / failed: \(error)")
            }
            return uuidByAppID.mapValues { _ in PriceState.failed }
        }

        // 稀疏数组：没有条目或没有 Steam 激活的 deal 都是没价格 / Sparse response: no entry or no Steam deal means no price
        var states: [Int: PriceState] = [:]
        for (appID, uuid) in uuidByAppID {
            let game = games.entry(for: uuid)
            let deal = game?.bestSteamDeal
            if let deal {
                states[appID] = .loaded(deal)
            } else {
                states[appID] = .noPrice
            }
        }
        return states
    }

    // MARK: - UUID lookup

    /// 一次 lookup 的结果 / The outcome of one lookup
    private enum LookupResult {
        case found(String)
        case notFound
        case failed
    }

    /// 并行 lookup 一批 appID；没有要查的直接返回空 / Looks up a batch of appIDs in parallel; empty when there is nothing to look up
    private func lookUpUUIDs(for appIDs: [Int]) async -> [Int: LookupResult] {
        guard !appIDs.isEmpty else { return [:] }

        return await withTaskGroup(of: (Int, LookupResult).self) { group in
            for appID in appIDs {
                group.addTask {
                    let result = await Self.lookUpUUID(appID: appID)
                    return (appID, result)
                }
            }

            var results: [Int: LookupResult] = [:]
            for await (appID, result) in group {
                results[appID] = result
            }
            return results
        }
    }

    /// 按 Steam appID 查一个 ITAD UUID；不回写 Firestore / Looks up one ITAD UUID by Steam appID; not written back to Firestore
    private static func lookUpUUID(appID: Int) async -> LookupResult {
        do {
            let response = try await ITADAPI.lookupUUID(appID: appID)
            guard response.found, let game = response.game else {
                return .notFound
            }
            return .found(game.id)
        } catch {
            if !Task.isCancelled {
                print("[WishlistViewModel] UUID lookup failed / failed: \(error)")
            }
            return .failed
        }
    }
}
