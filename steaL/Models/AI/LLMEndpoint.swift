//
//  LLMEndpoint.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import Foundation

/// 一次 LLM 请求发往哪里、用哪个 key 和模型。
/// Where an LLM request goes, and which key and model it uses.
///
/// 由调用方组好传给 `LLMAPI`，`LLMAPI` 自己不读配置：内置的来自 `Config`，用户自己的服务商来自 SwiftData 与 Keychain。
/// The caller builds it and hands it to `LLMAPI`, which never reads configuration itself: the built-in one comes
/// from `Config`, a user's own provider from SwiftData and the Keychain.
struct LLMEndpoint: Sendable {

    /// OpenAI Chat Completions 兼容的 base URL，如 `https://api.deepseek.com`。
    /// An OpenAI Chat Completions compatible base URL, such as `https://api.deepseek.com`.
    let baseURL: String

    /// 放进 `Authorization: Bearer` 的 API key。
    /// The API key sent as `Authorization: Bearer`.
    ///
    /// 不要打进日志，也不要存进任何状态，用完即弃。
    /// Never log it or keep it in any state; it's dropped after the request.
    let apiKey: String

    /// 请求里 `model` 字段的值，如 `deepseek-flash`。
    /// The value of the request's `model` field, such as `deepseek-flash`.
    let model: String
}

// MARK: - 内置 / Built-in

extension LLMEndpoint {

    /// App 内置的服务商，三项来自 `Secrets.xcconfig`。
    /// The app's built-in provider, with all three values from `Secrets.xcconfig`.
    static let builtIn = LLMEndpoint(
        baseURL: Config.llmAPIURL,
        apiKey: Config.llmAPIKey,
        model: Config.llmModel
    )
}
