//
//  GameDetailViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import Foundation
import OSLog

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
        /// 请求成功但 Steam 上没有这款游戏（下架或当前地区不卖）；不是失败，重试也不会变 /
        /// The request worked but Steam has no such game (delisted or not sold here); not a failure, and a retry would not help
        case notFound
        /// 加载失败，携带归一化后的错误 / Load failed, carrying the normalised error
        case failed(LoadError)
    }

    /// 比价区块自己的状态：慢一块不该把整页拖成加载态；请求失败是 `.failed`（给 Retry），
    /// 查不到看游戏类型（`.notFound` 或 `.hidden`）/
    /// The price section's own state: one slow section must not hold the page loading; a failure is
    /// `.failed` (with a retry), while a miss depends on the kind of game (`.notFound` or `.hidden`)
    enum PricesState: Equatable {
        /// ViewModel 判定这个类型不该有比价 / The view model decided this kind has no prices
        case hidden
        /// 正在查 / Asking
        case loading
        /// 比价到手，含历史低价 / The prices are in, historical lows included
        case loaded(ITADPriceGame)
        /// 比价到手，但不显示历史低价（永久免费）/ The prices are in, minus the historical lows
        case loadedWithoutHistory(ITADPriceGame)
        /// 请求成功，但 ITAD 没收录或当前地区没有报价；重试也不会变 /
        /// The request worked, but ITAD does not list the game or has no offers here; a retry would not help
        case notFound
        /// 请求失败（UUID 查找或 `prices/v3`），给 Retry / The request failed (UUID lookup or `prices/v3`); offers a retry
        case failed

        /// 把请求成功拿到的比价落成状态，`nil` 是查不到；请求失败不走这里 /
        /// Resolves a successful prices result into a state, `nil` being a miss; failures never get here
        static func from(_ prices: ITADPriceGame?, kind: SteamAppDetails.GameKind) -> PricesState {
            guard let prices, !prices.isEmpty else {
                // 未发售没价是还没开预购，不算「没有数据」/ No prices before release just means no pre-order yet
                if kind == .unreleased {
                    return .hidden
                }

                return .notFound
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
        /// ITAD 没收录这款游戏；重试也不会变 / ITAD does not list this game; a retry would not help
        case notFound
        /// 请求失败（UUID 查找或 `info/v2`），给 Retry / The request failed (UUID lookup or `info/v2`); offers a retry
        case failed
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

    /// AI 购买建议的输入；拼不出来是 `nil`，详情页据此决定显不显示 AI 入口 /
    /// The AI purchase advice input; `nil` when it cannot be built, which also hides the AI entry
    ///
    /// 要 Steam 详情（试玩版与永久免费不给，见 `GameKind.canGetAIAdvice`）、比价（含 Steam 激活的 deal），
    /// 并等信息区块加载完；信息失败或没收录也照样给，AI 只是少了口碑与在线人数 /
    /// Needs the Steam details (never for a demo or a free-forever title, see `GameKind.canGetAIAdvice`),
    /// prices with a Steam-activating deal, and waits for the info section to finish;
    /// a failed or missing info section still counts, the model just lacks reviews and players
    var adviceContext: DetailAIAdviceContext? {
        guard case .loaded(let details) = state else { return nil }

        let gameKind = details.gameKind
        guard gameKind.canGetAIAdvice else { return nil }

        let prices: ITADPriceGame
        switch pricesState {
        case .loaded(let loadedPrices), .loadedWithoutHistory(let loadedPrices):
            prices = loadedPrices
        case .hidden, .loading, .notFound, .failed:
            return nil
        }

        guard prices.bestSteamDeal != nil else { return nil }

        let info: ITADGameInfoResponse?
        switch infoState {
        case .loading:
            return nil
        case .loaded(let loadedInfo):
            info = loadedInfo
        case .hidden, .notFound, .failed:
            info = nil
        }

        return DetailAIAdviceContext(title: title, details: details, prices: prices, info: info)
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
            let fetchedDetails = try await SteamAPI.fetchAppDetails(
                appID: appID,
                region: region,
                language: language
            )

            // 视图消失或请求被取消后不再写状态 / No writes once the view is gone or cancelled
            guard !Task.isCancelled else { return }

            // 查不到不是失败，不走 `LoadError` / A miss is not a failure, so it skips `LoadError`
            guard let details = fetchedDetails else {
                state = .notFound
                return
            }

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
            // 永久免费也要查：在别的商店未必免费 / Free-to-play is loaded too; other stores may still sell it
            case .unreleased, .freeForever, .paid:
                pricesState = .loading
                infoState = .loading
            }

            // 两个 ITAD 区块共用一次 UUID 查找，已有就跳过 / Both ITAD sections share one UUID lookup
            let resolvedUUID: String?
            do {
                resolvedUUID = try await resolveUUID()
            } catch {
                // 请求失败，两块都给 Retry / The lookup failed, so both sections offer a retry
                pricesState = .failed
                infoState = .failed
                return
            }

            // ITAD 没收录这款游戏 / ITAD does not list this game
            guard let resolvedUUID else {
                pricesState = .from(nil, kind: details.gameKind)
                infoState = .notFound
                return
            }

            async let prices = fetchPricesState(uuid: resolvedUUID, region: region, kind: details.gameKind)
            async let info = fetchInfoState(uuid: resolvedUUID)

            pricesState = await prices
            infoState = await info

        } catch {
            guard !Task.isCancelled else { return }

            Logger.gameDetail.error("load failed / failed: \(String(describing: error))")

            state = .failed(LoadError.from(error))
        }
    }

    /// 重试 ITAD 比价，必要时先按 appID 查 UUID / Retries the ITAD prices, resolving the UUID when needed
    func retryPrices(region: AppRegion) async {
        // Retry 只在详情到手后可点，用它的类型决定是否显示历史低价 / Only reachable once the details are in
        guard case .loaded(let details) = state else { return }

        pricesState = .loading

        guard !Task.isCancelled else { return }

        let resolvedUUID: String?
        do {
            resolvedUUID = try await resolveUUID()
        } catch {
            pricesState = .failed
            return
        }

        guard let resolvedUUID else {
            pricesState = .from(nil, kind: details.gameKind)
            return
        }

        let newState = await fetchPricesState(uuid: resolvedUUID, region: region, kind: details.gameKind)
        guard !Task.isCancelled else { return }
        pricesState = newState
    }

    /// 查一次 ITAD 游戏信息，必要时先按 appID 查 UUID / Fetches the ITAD game info, resolving the UUID when needed
    func retryInfo() async {
        infoState = .loading

        guard !Task.isCancelled else { return }

        let resolvedUUID: String?
        do {
            resolvedUUID = try await resolveUUID()
        } catch {
            infoState = .failed
            return
        }

        guard let resolvedUUID else {
            infoState = .notFound
            return
        }

        let newState = await fetchInfoState(uuid: resolvedUUID)
        guard !Task.isCancelled else { return }
        infoState = newState
    }

    /// 查一次比价并落成状态：查不到交给 `PricesState.from`，请求失败是 `.failed` /
    /// Fetches the prices as a state: a miss goes through `PricesState.from`, a failure is `.failed`
    ///
    /// 响应是**稀疏数组**，只能按 `id` 取；查不到不是错误 /
    /// The response is a **sparse array**, found by `id`; a missing entry is not an error
    private func fetchPricesState(
        uuid: String,
        region: AppRegion,
        kind: SteamAppDetails.GameKind
    ) async -> PricesState {
        do {
            let response = try await ITADAPI.fetchPrices(uuids: [uuid], region: region)
            let prices = response.entry(for: uuid)
            return .from(prices, kind: kind)

        } catch {
            // 取消是正常的（页面离开），其余失败只记日志 / Cancellation is normal; the rest is just logged
            if !(error is CancellationError) {
                Logger.gameDetail.error("prices failed / failed: \(String(describing: error))")
            }
            return .failed
        }
    }

    /// 查一次 ITAD 游戏信息并落成状态；UUID 已确认收录，所以只有成功或失败 /
    /// Fetches the ITAD game info as a state; the UUID is known to be listed, so it either loads or fails
    private func fetchInfoState(uuid: String) async -> InfoState {
        do {
            let info = try await ITADAPI.fetchGameInfo(uuid: uuid)
            return .loaded(info)
        } catch {
            if !(error is CancellationError) {
                Logger.gameDetail.error("info failed / failed: \(String(describing: error))")
            }
            return .failed
        }
    }

    /// 优先使用路由 UUID，否则按 Steam appID 查一次 / Uses the route UUID, or looks it up once by Steam appID
    ///
    /// - Returns: ITAD 没收录时是 `nil` / `nil` when ITAD does not list the game
    /// - Throws: 请求失败时抛出，调用方据此给 Retry / rethrows a failed request so the caller can offer a retry
    private func resolveUUID() async throws -> String? {
        if let uuid {
            return uuid
        }

        let response: ITADGameLookupResponse
        do {
            response = try await ITADAPI.lookupUUID(appID: appID)
        } catch {
            if !(error is CancellationError) {
                Logger.gameDetail.error("UUID lookup failed / failed: \(String(describing: error))")
            }
            throw error
        }

        guard response.found, let resolvedUUID = response.game?.id else {
            return nil
        }

        self.uuid = resolvedUUID
        return resolvedUUID
    }
}
