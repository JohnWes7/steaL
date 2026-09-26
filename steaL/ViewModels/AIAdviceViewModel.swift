//
//  AIAdviceViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import Foundation

/// 详情页 AI 购买建议的状态机；由 `GameDetailView` 用 `@State` 持有，结果跟着详情页，关掉 sheet 还在 /
/// State machine for the detail page's AI purchase advice; owned by `GameDetailView` via `@State`,
/// so the result lives with the page and survives closing the sheet
@MainActor
@Observable
final class AIAdviceViewModel {

    /// 整张 sheet 只有这一个状态 / The whole sheet runs on this one state
    enum State: Equatable {
        /// 还没请求过；sheet 当成加载中显示 / Not requested yet; the sheet shows it as loading
        case idle
        /// 正在生成 / Generating
        case loading
        /// AI 结果到手 / The advice is in
        case loaded(DetailAIAdviceResponse)
        /// 生成失败，给 Retry / Generation failed; offers a retry
        case failed(LoadError)
    }

    /// 当前状态 / Current state
    private(set) var state: State = .idle

    /// 只在还没请求过时生成：反复开关 sheet 不会重复请求；生成中、已有结果、失败都不自动重发 /
    /// Generates only when nothing was requested yet, so reopening the sheet never repeats the call;
    /// a request in flight, a result, or a failure is left alone
    func generateIfNeeded(context: DetailAIAdviceContext, language: AppLanguage) async {
        guard case .idle = state else { return }

        // 下一步：组 prompt → `LLMAPI.respond` → 写状态 / Next: build the prompt, call `LLMAPI.respond`, write the state
    }
}
