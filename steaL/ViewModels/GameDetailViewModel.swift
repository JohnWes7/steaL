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
        /// 还没开始查 / Not asked for yet
        case idle
        /// 正在查 / Asking
        case loading
        /// 查到了 / The prices are in
        case loaded(ITADPriceGame)
        /// 问过了，没有可显示的价格 / Asked, and there is nothing to show
        case unavailable
    }

    /// 页面当前状态，决定整页显示什么 / Current state of the page; decides what the whole page shows
    ///
    /// 详情是硬门槛，所以它是状态机的主轴 / The details are the hard requirement, so they drive the
    /// state machine
    private(set) var state: State = .loading

    /// ITAD 比价的状态，独立于主状态 / The ITAD prices' own state, independent of the main one
    private(set) var pricesState: PricesState = .idle

    /// 加载一页详情 / Loads one detail page
    ///
    /// 参数由视图传入而不是存成属性：`@State` 只在视图身份建立时求值一次，
    /// 存下来的 route 会在推入新页面时变成旧值 /
    /// Parameters are handed in rather than stored: a `@State` view model is only built once per
    /// view identity, so a stored route would go stale on the next push
    ///
    /// - Parameters:
    ///   - route: 导航携带的 appID 与 uuid / the appID and uuid handed over by navigation
    ///   - region: 商店区域，决定货币 / store region, drives the currency
    ///   - language: 语言，Steam 会据此本地化标题与描述 / language, Steam localises the copy with it
    ///   - isRetry: 重试时保留旧内容，避免页面闪一下空白 / keeps the current content on a retry
    func load(
        route: GameDetailRoute,
        region: AppRegion,
        language: AppLanguage,
        isRetry: Bool = false
    ) async {
        // 重试时不清空，首轮才先摆出加载态 / Only the first attempt blanks the page
        if !isRetry {
            state = .loading
            pricesState = .idle
        }

        // 硬门槛：拿不到 Steam 详情就没有详情页 / Hard requirement: no Steam details, no page
        do {
            let details = try await SteamAPI.fetchAppDetails(
                appID: route.appID,
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

            // 比价是可选增强，失败只当没有，不拖垮整页；没有 uuid 时直接跳过，状态留在 `.idle` /
            // Prices are optional: a failure just means "no price data". Without a uuid there is
            // nothing to ask for, so the state stays `.idle`
            if route.uuid != nil {
                pricesState = .loading
                let prices = await fetchPricesIfPossible(route: route, region: region)
                pricesState = prices.map(PricesState.loaded) ?? .unavailable
            }

        } catch {
            guard !Task.isCancelled else { return }

            print("[GameDetailViewModel] load failed / failed: \(error)")
            if let steamError = error as? SteamAPIError {
                print("[GameDetailViewModel] detailed error / detail: \(steamError.debugDescription)")
            }

            state = .failed(LoadError.from(error))
        }
    }

    /// 拿到这个游戏在各商店的价格，拿不到就返回 `nil` /
    /// Fetches the game's prices, returning `nil` when there is nothing to show
    ///
    /// 响应是**稀疏数组**，只能按 `id` 取；查不到（没有折扣价）不是错误 /
    /// The response is a **sparse array**, so the entry has to be found by `id`; a missing entry
    /// (the game currently has no discounted price) is not an error
    private func fetchPricesIfPossible(route: GameDetailRoute, region: AppRegion) async -> ITADPriceGame? {
        guard let uuid = route.uuid else { return nil }

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
}
