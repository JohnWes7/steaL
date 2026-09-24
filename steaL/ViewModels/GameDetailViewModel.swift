//
//  GameDetailViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import Foundation

/// 游戏详情页的数据状态机：Steam 的 `appdetails` 是硬门槛，ITAD 比价失败只少一块区块 /
/// State machine for the game detail page: Steam's `appdetails` is the hard requirement, while a
/// failed ITAD call only costs one section
@MainActor
@Observable
final class GameDetailViewModel {

    /// Steam 游戏 App ID / The Steam game app ID
    var appID: Int {
        didSet {
            if appID != oldValue {
                uuid = nil
            }
        }
    }

    /// 导航传入的初始标题 / The initial title received from navigation
    let initialTitle: String

    /// 整页状态；详情是硬门槛 / The page's state; the details are the hard requirement
    enum State: Equatable {
        /// 首次加载或重试中 / First load, or a retry in progress
        case loading
        /// Steam 详情已拿到 / The Steam details are in
        case loaded(SteamAppDetails)
        /// 加载失败，携带归一化后的错误 / Load failed, carrying the normalised error
        case failed(LoadError)
    }

    /// 比价区块自己的状态：慢一块不该把整页拖成加载态，查不到与请求失败都归 `.unavailable` /
    /// The price section's own state: one slow section must not hold the page loading, and a miss
    /// and a failure both become `.unavailable`
    enum PricesState: Equatable {
        /// ViewModel 判定这个类型不该有比价 / The view model decided this kind has no prices
        case hidden
        /// 正在查 / Asking
        case loading
        /// 比价到手，含历史低价 / The prices are in, historical lows included
        case loaded(ITADPriceGame)
        /// 比价到手，但不显示历史低价（永久免费）/ The prices are in, minus the historical lows
        case loadedWithoutHistory(ITADPriceGame)
        /// 问过了，没有可显示的价格 / Asked, and there is nothing to show
        case unavailable

        /// 把一次比价的结果落成状态 / Resolves one prices result into a state
        static func from(_ prices: ITADPriceGame?, kind: SteamAppDetails.GameKind) -> PricesState {
            guard let prices, !prices.isEmpty else {
                // 未发售没价是还没开预购，不算「没有数据」/ No prices before release just means no pre-order yet
                if kind == .unreleased {
                    return .hidden
                }

                return .unavailable
            }

            if kind.showsPriceHistory {
                return .loaded(prices)
            }

            return .loadedWithoutHistory(prices)
        }
    }

    /// ITAD 游戏信息区块自己的状态 / The info section's own state
    enum InfoState: Equatable {
        /// ViewModel 判定这个类型不该有信息 / The view model decided this kind has no info
        case hidden
        /// 正在查 / Asking
        case loading
        /// 信息到手 / The info is in
        case loaded(ITADGameInfoResponse)
        /// 问过了，没有可显示的信息 / Asked, and there is no info to show
        case unavailable
    }

    /// 页面当前状态 / Current state of the page
    private(set) var state: State = .loading

    /// ITAD 比价的状态；详情到手前是 `.hidden` / The ITAD prices' state, hidden until the details are in
    private(set) var pricesState: PricesState = .hidden

    /// ITAD 游戏信息的状态 / The ITAD info state
    private(set) var infoState: InfoState = .hidden

    /// ITAD 请求用的游戏 UUID / The game UUID used for ITAD requests
    private(set) var uuid: String?

    /// 用导航参数创建状态机 / Creates the state machine from its navigation payload
    init(route: GameDetailRoute) {
        appID = route.appID
        initialTitle = route.title
        uuid = route.uuid
    }

    /// 当前页面标题；优先使用 Steam 返回的名称 / Current page title, preferring Steam's name
    var title: String {
        if case .loaded(let details) = state,
            let name = details.name,
            !name.isEmpty
        {
            return name
        }

        return initialTitle
    }

    /// 加载一页详情 / Loads one detail page
    ///
    /// - Parameter region: 商店区域，决定货币 / the store region, which drives the currency
    /// - Parameter language: 语言，Steam 据此本地化文案 / the language Steam localises the copy with
    /// - Parameter isRetry: 重试时保留旧内容 / keeps the current content on a retry
    func load(
        region: AppRegion,
        language: AppLanguage,
        isRetry: Bool = false
    ) async {
        // 重试时不清空，首轮才先摆出加载态 / Only the first attempt blanks the page
        if !isRetry {
            state = .loading
        }

        // 硬门槛：拿不到 Steam 详情就没有详情页 / Hard requirement: no Steam details, no page
        do {
            let details = try await SteamAPI.fetchAppDetails(
                appID: appID,
                region: region,
                language: language
            )

            // 视图消失或请求被取消后不再写状态 / No writes once the view is gone or cancelled
            guard !Task.isCancelled else { return }

            // 详情一到就切状态，比价随后补上，不挡首屏 / Switch as the details land; prices fill in later
            state = .loaded(details)

            // 游戏类型决定 ITAD 的两块要不要查 / The kind of game decides which ITAD section is loaded
            switch details.gameKind {
            // 试玩版两块都不查：`prices/v3` 恒为 `[]` / A demo loads neither: `prices/v3` is always empty
            case .demo:
                pricesState = .hidden
                infoState = .hidden
                return

            // 未发售也照查：开了预购就有价（Persona 4 Revival），没开由 `from` 藏起来 /
            // Unreleased is loaded too: a pre-order has prices, and `from` hides the rest.
            // 免费也和付费一样要查：限时免费有原价，永久免费在别处未必免费 / Free is loaded like paid
            case .unreleased, .freeToKeep, .freeForever, .paid:
                pricesState = .loading
                infoState = .loading
            }

            // 两个 ITAD 区块共用一次 UUID 查找，已有就跳过 / Both ITAD sections share one UUID lookup
            guard let uuid = await resolveUUID() else {
                pricesState = .from(nil, kind: details.gameKind)
                infoState = .unavailable
                return
            }

            async let prices = fetchPricesIfPossible(uuid: uuid, region: region)
            async let info = fetchInfoIfPossible(uuid: uuid)

            pricesState = .from(await prices, kind: details.gameKind)
            infoState = (await info).map(InfoState.loaded) ?? .unavailable

        } catch {
            guard !Task.isCancelled else { return }

            print("[GameDetailViewModel] load failed / failed: \(error)")
            if let steamError = error as? SteamAPIError {
                print("[GameDetailViewModel] detailed error / detail: \(steamError.debugDescription)")
            }

            state = .failed(LoadError.from(error))
        }
    }

    /// 重试 ITAD 比价，必要时先按 appID 查 UUID / Retries the ITAD prices, resolving the UUID when needed
    func retryPrices(region: AppRegion) async {
        // Retry 只在详情到手后可点，用它的类型决定是否显示历史低价 / Only reachable once the details are in
        guard case .loaded(let details) = state else { return }

        pricesState = .loading

        guard !Task.isCancelled else { return }
        guard let uuid = await resolveUUID() else {
            pricesState = .from(nil, kind: details.gameKind)
            return
        }

        let prices = await fetchPricesIfPossible(uuid: uuid, region: region)
        guard !Task.isCancelled else { return }
        pricesState = .from(prices, kind: details.gameKind)
    }

    /// 查一次 ITAD 游戏信息，必要时先按 appID 查 UUID / Fetches the ITAD game info, resolving the UUID when needed
    func retryInfo() async {
        infoState = .loading

        guard !Task.isCancelled else { return }
        guard let uuid = await resolveUUID() else {
            infoState = .unavailable
            return
        }

        let info = await fetchInfoIfPossible(uuid: uuid)
        guard !Task.isCancelled else { return }
        infoState = info.map(InfoState.loaded) ?? .unavailable
    }

    /// 拿到这个游戏在各商店的价格，没有就返回 `nil` / Fetches the prices, `nil` when there are none
    ///
    /// 响应是**稀疏数组**，只能按 `id` 取；查不到不是错误 /
    /// The response is a **sparse array**, found by `id`; a missing entry is not an error
    private func fetchPricesIfPossible(uuid: String, region: AppRegion) async -> ITADPriceGame? {
        do {
            let response = try await ITADAPI.fetchPrices(uuids: [uuid], region: region)
            return response.entry(for: uuid)

        } catch {
            // 取消是正常的（页面离开），其余失败只记日志 / Cancellation is normal; the rest is just logged
            if !(error is CancellationError) {
                print("[GameDetailViewModel] prices failed / failed: \(error)")
            }
            return nil
        }
    }

    /// 获取 ITAD 游戏信息，失败时静默降级 / Fetches the ITAD game info, degrading silently on failure
    private func fetchInfoIfPossible(uuid: String) async -> ITADGameInfoResponse? {
        do {
            return try await ITADAPI.fetchGameInfo(uuid: uuid)
        } catch {
            if !(error is CancellationError) {
                print("[GameDetailViewModel] info failed / failed: \(error)")
            }
            return nil
        }
    }

    /// 优先使用路由 UUID，否则按 Steam appID 查一次 / Uses the route UUID, or looks it up once by Steam appID
    private func resolveUUID() async -> String? {
        if let uuid {
            return uuid
        }

        do {
            let response = try await ITADAPI.lookupUUID(appID: appID)
            guard !Task.isCancelled else { return nil }

            guard response.found, let resolvedUUID = response.game?.id else {
                return nil
            }

            self.uuid = resolvedUUID
            return resolvedUUID
        } catch {
            if !(error is CancellationError) {
                print("[GameDetailViewModel] UUID lookup failed / failed: \(error)")
            }
            return nil
        }
    }
}
