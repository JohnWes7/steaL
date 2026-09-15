//
//  GameDetailViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import Foundation

/// 游戏详情页的数据状态机 / State machine for the game detail page
///
/// Steam 的 `appdetails` 是硬门槛，ITAD 的比价是可选增强：拿不到评分与比价时页面照常渲染，
/// 只是少一块 /
/// Steam's `appdetails` is the hard requirement; the ITAD prices are an enhancement, so a failure
/// there only costs one section instead of the whole page
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

    /// 页面当前状态 / Current state of the page
    enum State: Equatable {
        /// 首次加载或重试中 / First load, or a retry in progress
        case loading
        /// Steam 详情已拿到 / The Steam details are in
        case loaded(SteamAppDetails)
        /// 加载失败，携带归一化后的错误 / Load failed, carrying the normalised error
        case failed(LoadError)
    }

    /// 比价区块自己的状态 / The price section's own state
    ///
    /// 它是页面上的一块，慢一点不该把整页拖成加载态，所以单独一个状态机 /
    /// It is one section of the page, so it must not hold the whole page in a loading state; hence
    /// a state machine of its own
    ///
    /// 查不到与请求失败归为同一种结果（`.unavailable`）—— 对界面来说都只是「没有比价数据」/
    /// "Nothing found" and "the request failed" collapse into `.unavailable`: the UI cannot tell
    /// them apart anyway, and a failure here never fails the page
    enum PricesState: Equatable {
        /// ViewModel 判定这个类型不该出现价格区块 / The view model decided this kind has no price section
        case hidden
        /// 正在查 / Asking
        case loading
        /// 查到了 / The prices are in
        case loaded(ITADPriceGame)
        /// 问过了，没有可显示的价格 / Asked, and there is nothing to show
        case unavailable

        /// 一次比价请求的结果落成的状态 / The state a prices result resolves to
        static func from(_ prices: ITADPriceGame?) -> PricesState {
            if let prices, !prices.isEmpty {
                return .loaded(prices)
            }

            return .unavailable
        }
    }

    /// ITAD 游戏信息区块自己的状态 / The info section's own state
    enum InfoState: Equatable {
        /// ViewModel 判定这个类型不该出现信息区块 / The view model decided this kind has no info section
        case hidden
        /// 正在查 / Asking
        case loading
        /// 查到了 / The info is in
        case loaded(ITADGameInfoResponse)
        /// 问过了，没有可显示的信息 / Asked, and there is no info to show
        case unavailable
    }

    /// 页面当前状态，决定整页显示什么 / Current state of the page; decides what the whole page shows
    ///
    /// 详情是硬门槛，所以它是状态机的主轴 / The details are the hard requirement, so they drive the
    /// state machine
    private(set) var state: State = .loading

    /// ITAD 比价的状态，独立于主状态；详情到手前是 `.hidden` /
    /// The ITAD prices' own state, independent of the main one; `.hidden` until the details are in
    private(set) var pricesState: PricesState = .hidden

    /// ITAD 游戏信息的状态，独立于页面主状态 / The ITAD info state, independent of the page state
    private(set) var infoState: InfoState = .hidden

    /// 可用于 ITAD 请求的游戏 UUID / The game UUID used for ITAD requests
    private(set) var uuid: String?

    /// 用导航参数创建详情页状态机 / Creates the detail state machine from its navigation payload
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
    /// - Parameters:
    ///   - region: 商店区域，决定货币 / store region, drives the currency
    ///   - language: 语言，Steam 会据此本地化标题与描述 / language, Steam localises the copy with it
    ///   - isRetry: 重试时保留旧内容，避免页面闪一下空白 / keeps the current content on a retry
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

            // 页面已经离开（或在飞请求被 `.task` 取消）时不写状态 /
            // Do not write state once the view is gone or the request was cancelled
            guard !Task.isCancelled else { return }

            // 详情到手就切状态，页面马上渲染；比价随后单独补上，不挡首屏 /
            // Switch as soon as the details land so the page renders; the prices fill in afterwards
            // without holding the first frame back
            state = .loaded(details)

            // 游戏类型决定 ITAD 的两块要不要查 / The kind of game decides which ITAD section is loaded
            switch details.gameKind {
            // 试玩版两块都不查：`prices/v3` 对试玩版恒为 `[]`，ITAD 也只收录老试玩版的信息 /
            // A demo loads neither: `prices/v3` is always empty for one, and ITAD only covers older demos
            case .demo:
                pricesState = .hidden
                infoState = .hidden
                return

            // 还没发售只补 ITAD 信息，没有可比的价 /
            // An unreleased title only needs the ITAD info; there is no price to compare
            case .unreleased:
                pricesState = .hidden
                await retryInfo()
                return

            // 免费与付费一样要查：限时免费的原价还在，永久免费在别的商店也未必免费 /
            // Free is loaded like paid: a limited-time free title still has its regular price, and a
            // permanently free one is not free in every shop
            case .free, .paid:
                pricesState = .loading
                infoState = .loading
            }

            // ITAD 增强数据共用一次 UUID lookup；已有 UUID 时不查 lookup /
            // The ITAD enhancements share one UUID lookup; an existing UUID skips that lookup
            guard let uuid = await resolveUUID() else {
                pricesState = .unavailable
                infoState = .unavailable
                return
            }

            async let prices = fetchPricesIfPossible(uuid: uuid, region: region)
            async let info = fetchInfoIfPossible(uuid: uuid)

            pricesState = .from(await prices)
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

    /// 重试 ITAD 比价；没有 UUID 时先按 Steam appID 查找 / Retries ITAD prices, resolving the UUID first when needed
    func retryPrices(region: AppRegion) async {
        pricesState = .loading

        guard !Task.isCancelled else { return }
        guard let uuid = await resolveUUID() else {
            pricesState = .unavailable
            return
        }

        let prices = await fetchPricesIfPossible(uuid: uuid, region: region)
        guard !Task.isCancelled else { return }
        pricesState = .from(prices)
    }

    /// 查一次 ITAD 游戏信息；没有 UUID 时先按 Steam appID 查找，拿不到就当作没有 /
    /// Fetches the ITAD game info once, resolving the UUID first when needed, and treats a miss as
    /// "nothing"
    ///
    /// 它同时是首次加载的入口：未发售的详情页也走这里，所以 `load` 里不再另起一份 /
    /// It doubles as the first-load entry point for unreleased titles, so `load` reuses it instead of
    /// duplicating the sequence
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

    /// 拿到这个游戏在各商店的价格，拿不到就返回 `nil` /
    /// Fetches the game's prices, returning `nil` when there is nothing to show
    ///
    /// 响应是**稀疏数组**，只能按 `id` 取；查不到（没有价格数据）不是错误 /
    /// The response is a **sparse array**, so the entry has to be found by `id`; a missing entry
    /// (the game has no price data) is not an error
    private func fetchPricesIfPossible(uuid: String, region: AppRegion) async -> ITADPriceGame? {
        do {
            let response = try await ITADAPI.fetchPrices(uuids: [uuid], region: region)
            return response.entry(for: uuid)

        } catch {
            // 取消是正常的（页面离开），其余失败只记日志 /
            // Cancellation is normal (the page went away); anything else is only logged
            if !(error is CancellationError) {
                print("[GameDetailViewModel] prices failed / failed: \(error)")
            }
            return nil
        }
    }

    /// 获取 ITAD 游戏信息，失败时静默降级 / Fetches ITAD game info, degrading silently on failure
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
