//
//  GameDetailViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import Foundation

/// 游戏详情页的数据状态机 / State machine for the game detail page
///
/// 本轮只接 Steam 的 `appdetails`，ITAD 的比价与评分等 DTO 写好后接入 `load` /
/// Only Steam's `appdetails` is wired up so far; the ITAD price and info DTOs slot into
/// `load` once they exist
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

    /// 页面当前状态 / Current state of the page
    private(set) var state: State = .loading

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

            state = .loaded(details)

        } catch {
            guard !Task.isCancelled else { return }

            print("[GameDetailViewModel] load failed / failed: \(error)")
            if let steamError = error as? SteamAPIError {
                print("[GameDetailViewModel] detailed error / detail: \(steamError.debugDescription)")
            }

            state = .failed(LoadError.from(error))
        }
    }
}
