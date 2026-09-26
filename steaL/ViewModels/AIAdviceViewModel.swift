//
//  AIAdviceViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import Foundation
import OSLog

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
        /// AI 结果到手，带生成时间与模型名（sheet 底部显示「哪个模型、多久前生成」）/
        /// The advice is in, with when and by which model it was generated, both shown at the bottom of the sheet
        case loaded(DetailAIAdviceResponse, generatedAt: Date, model: String)
        /// 生成失败，给 Retry / Generation failed; offers a retry
        case failed(LoadError)
    }

    /// 当前状态 / Current state
    private(set) var state: State

    /// 平时从 `.idle` 开始；预览可以直接给一个状态 / Starts at `.idle`; previews can pass a state directly
    init(state: State = .idle) {
        self.state = state
    }

    /// 只在还没请求过时生成：反复开关 sheet 不会重复请求；生成中、已有结果、失败都不自动重发 /
    /// Generates only when nothing was requested yet, so reopening the sheet never repeats the call;
    /// a request in flight, a result, or a failure is left alone
    func generateIfNeeded(context: DetailAIAdviceContext, language: AppLanguage) async {
        guard case .idle = state else { return }

        await generate(context: context, language: language)
    }

    /// sheet 的 Retry / Regenerate：不管上次的结果重新生成；生成中不重复发 /
    /// The sheet's Retry and Regenerate: generates again whatever the last result; a request in flight is left alone
    func regenerate(context: DetailAIAdviceContext, language: AppLanguage) async {
        if case .loading = state {
            return
        }

        await generate(context: context, language: language)
    }

    /// 组 prompt → 远程生成 → 写状态 / Builds the prompt, generates remotely, and writes the state
    private func generate(context: DetailAIAdviceContext, language: AppLanguage) async {
        // 在第一次 `await` 之前置 loading，连点两下也只会发一次 / Set before the first `await`, so a double tap sends once
        state = .loading

        do {
            let systemPrompt = try DetailAIAdvicePrompt.systemPrompt(language: language)
            let userPrompt = try DetailAIAdvicePrompt.userMessage(context: context)

            let advice = try await LLMAPI.respond(
                systemPrompt: systemPrompt,
                userPrompt: userPrompt,
                generating: DetailAIAdviceResponse.self
            )

            // 被取消就退回 idle，否则卡在 loading、之后不会再生成 / Back to idle on cancel, or it would stick at loading for good
            guard !Task.isCancelled else {
                state = .idle
                return
            }

            // 记的是请求时用的模型名；之后接端上兜底，走端上时换成端上模型的名字 /
            // The model name sent with the request; the on-device fallback will record its own name instead
            state = .loaded(advice, generatedAt: .now, model: Config.llmModel)

        } catch {
            guard !Task.isCancelled else {
                state = .idle
                return
            }

            Logger.gameDetail.error("AI advice failed / failed: \(String(describing: error))")

            state = .failed(LoadError.from(error))
        }
    }
}
