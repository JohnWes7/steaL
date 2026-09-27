//
//  LLMProvider.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import Foundation
import SwiftData

/// 用户自己添加的 LLM 服务商，走 OpenAI Chat Completions 兼容协议。
/// An LLM provider the user added, speaking the OpenAI Chat Completions compatible protocol.
///
/// API key 不在这里，按 `id` 存在 Keychain（见 `APIKeyStore`）。
/// The API key isn't stored here but in the Keychain under `id` (see `APIKeyStore`).
@Model
final class LLMProvider {

    /// 唯一标识，也是 Keychain 里存取 API key 的 account。
    /// The unique identifier, also the Keychain account that holds the API key.
    @Attribute(.unique) private(set) var id = UUID()

    /// 用户起的名字，如 `My OpenRouter`。
    /// The name the user gave it, such as `My OpenRouter`.
    var name: String = ""

    /// base URL，如 `https://openrouter.ai/api/v1`。
    /// The base URL, such as `https://openrouter.ai/api/v1`.
    ///
    /// 请求时去掉结尾的 `/` 再拼 `/chat/completions`。
    /// A trailing `/` is dropped before appending `/chat/completions` for a request.
    var baseURL: String = ""

    /// 添加时间，列表按它排序。
    /// When it was added; lists sort by it.
    private(set) var createdAt = Date.now

    // MARK: - 关系 / Relationships

    /// 这个服务商下的模型，删服务商时一起删（cascade）。
    /// The models under this provider, deleted along with it (cascade).
    ///
    /// 界面列表不读它，而是用 `@Query` 查 `LLMModel`：从模型那一侧改关系时，它不会通知界面刷新。
    /// Views don't list from it but query `LLMModel` instead, because it doesn't notify the view of changes made from
    /// the model's side.
    @Relationship(deleteRule: .cascade, inverse: \LLMModel.provider)
    var models: [LLMModel] = []

    // MARK: - 初始化 / Init

    /// 新建一个服务商。
    /// Creates a provider.
    ///
    /// - Parameters:
    ///   - name: 用户起的名字 / The name the user gave it.
    ///   - baseURL: 服务商的 base URL / The provider's base URL.
    init(name: String, baseURL: String) {
        self.name = name
        self.baseURL = baseURL
    }
}
