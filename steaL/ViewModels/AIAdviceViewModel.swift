//
//  AIAdviceViewModel.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import Foundation
import OSLog

/// 详情页 AI 购买建议的状态机。
/// State machine for the detail page's AI purchase advice.
///
/// 由 `GameDetailView` 用 `@State` 持有，结果跟着详情页，关掉 sheet 还在。
/// `GameDetailView` owns it via `@State`, so the result lives with the page and survives closing the sheet.
@MainActor
@Observable
final class AIAdviceViewModel {

    /// 整张 sheet 只有这一个状态。
    /// The whole sheet runs on this one state.
    enum State: Equatable {
        /// 还没请求过；sheet 当成加载中显示。
        /// Not requested yet; the sheet shows it as loading.
        case idle
        /// 正在生成。
        /// Generating.
        case loading
        /// AI 结果到手，带生成时间与模型名。
        /// The advice is in, with when and by which model it was generated.
        ///
        /// sheet 底部显示「哪个模型、多久前生成」。
        /// The bottom of the sheet shows both.
        case loaded(DetailAIAdviceResponse, generatedAt: Date, model: String)
        /// 选的是自己的服务商，但 Keychain 里没有它的 key；不给 Retry，要去设置里补。
        /// The chosen provider has no key in the Keychain; no retry, it has to be added in settings.
        ///
        /// 「没有」不是失败，所以不走 `LoadError`；从不加密的备份恢复时会遇到。
        /// Having none is not a failure, so it skips `LoadError`; it happens after restoring an unencrypted backup.
        case missingAPIKey(providerName: String)
        /// 生成失败，给 Retry。
        /// Generation failed; offers a retry.
        case failed(LoadError)
    }

    /// 当前状态。
    /// The current state.
    private(set) var state: State

    /// 创建状态机。
    /// Creates the state machine.
    ///
    /// - Parameter state: 初始状态，平时是 `.idle`，预览可以直接给一个 / The initial state; `.idle` normally, previews can pass one directly.
    init(state: State = .idle) {
        self.state = state
    }

    /// 只在还没请求过时生成。
    /// Generates only when nothing was requested yet.
    ///
    /// 反复开关 sheet 不会重复请求；生成中、已有结果、失败都不自动重发。
    /// Reopening the sheet never repeats the call; a request in flight, a result, or a failure is left alone.
    ///
    /// - Parameters:
    ///   - context: AI 的输入数据 / The input data for the AI.
    ///   - language: 回答用的语言 / The language to answer in.
    ///   - model: 设置里选中的模型，`nil` 是内置 / The model chosen in settings, `nil` for the built-in one.
    func generateIfNeeded(context: DetailAIAdviceContext, language: AppLanguage, model: LLMModel?) async {
        guard case .idle = state else { return }

        await generate(context: context, language: language, model: model)
    }

    /// sheet 的 Retry / Regenerate：不管上次的结果重新生成。
    /// The sheet's Retry and Regenerate: generates again whatever the last result.
    ///
    /// 生成中不重复发。
    /// A request in flight is left alone.
    ///
    /// - Parameters:
    ///   - context: AI 的输入数据 / The input data for the AI.
    ///   - language: 回答用的语言 / The language to answer in.
    ///   - model: 设置里选中的模型，`nil` 是内置 / The model chosen in settings, `nil` for the built-in one.
    func regenerate(context: DetailAIAdviceContext, language: AppLanguage, model: LLMModel?) async {
        if case .loading = state {
            return
        }

        await generate(context: context, language: language, model: model)
    }

    /// 组 endpoint 与 prompt → 远程生成 → 写状态。
    /// Builds the endpoint and the prompt, generates remotely, and writes the state.
    ///
    /// - Parameters:
    ///   - context: AI 的输入数据 / The input data for the AI.
    ///   - language: 回答用的语言 / The language to answer in.
    ///   - model: 设置里选中的模型，`nil` 是内置 / The model chosen in settings, `nil` for the built-in one.
    private func generate(context: DetailAIAdviceContext, language: AppLanguage, model: LLMModel?) async {
        // 在第一次 `await` 之前置 loading，连点两下也只会发一次。
        // Set before the first `await`, so a double tap sends once.
        state = .loading

        // `await` 期间模型可能被删，服务商名要先取出来。
        // The model may be deleted during the `await`, so read the provider name first.
        let providerName = model?.provider.name

        do {
            let resolvedEndpoint = try await makeEndpoint(for: model)

            guard let endpoint = resolvedEndpoint else {
                guard !Task.isCancelled else {
                    state = .idle
                    return
                }

                let missingKeyProviderName = providerName ?? ""
                state = .missingAPIKey(providerName: missingKeyProviderName)
                return
            }

            let systemPrompt = try DetailAIAdvicePrompt.systemPrompt(language: language)
            let userPrompt = try DetailAIAdvicePrompt.userMessage(context: context)

            let advice = try await LLMAPI.respond(
                systemPrompt: systemPrompt,
                userPrompt: userPrompt,
                generating: DetailAIAdviceResponse.self,
                using: endpoint
            )

            // 被取消就退回 idle，否则卡在 loading、之后不会再生成。
            // Back to idle on cancel, or it would stick at loading for good.
            guard !Task.isCancelled else {
                state = .idle
                return
            }

            // 记的是这次请求实际用的模型名。
            // Records the model this request actually used.
            state = .loaded(advice, generatedAt: .now, model: endpoint.model)

        } catch {
            guard !Task.isCancelled else {
                state = .idle
                return
            }

            Logger.gameDetail.error("AI advice failed / failed: \(String(describing: error))")

            state = .failed(LoadError.from(error))
        }
    }

    /// 按选中的模型组请求目标：没选是内置，选了就用它的服务商地址与 Keychain 里的 key。
    /// Builds the request target for the chosen model: the built-in one when none is chosen,
    /// otherwise its provider's URL and the key from the Keychain.
    ///
    /// 读 Keychain 之前先把要用的值取出来：`await` 期间模型可能被删掉，之后不能再碰它。
    /// The values are read before touching the Keychain: the model may be deleted during the `await`
    /// and must not be touched afterwards.
    ///
    /// - Parameter model: 设置里选中的模型，`nil` 是内置 / The model chosen in settings, `nil` for the built-in one.
    /// - Returns: 组好的 endpoint；Keychain 里没有这个服务商的 key 时是 `nil` /
    ///   The endpoint, or `nil` when the Keychain has no key for the provider.
    /// - Throws: 读 Keychain 失败时的 `APIKeyStoreError` / `APIKeyStoreError` when reading the Keychain fails.
    private func makeEndpoint(for model: LLMModel?) async throws -> LLMEndpoint? {
        guard let model else {
            return .builtIn
        }

        let provider = model.provider
        let providerID = provider.id
        let baseURL = provider.baseURL
        let modelName = model.name

        let apiKey = try await APIKeyStore.read(for: providerID)
        guard let apiKey else {
            return nil
        }

        return LLMEndpoint(baseURL: baseURL, apiKey: apiKey, model: modelName)
    }
}
