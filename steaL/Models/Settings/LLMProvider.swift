//
//  LLMProvider.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import Foundation
import SwiftData

/// 用户自己添加的 LLM 服务商（OpenAI Chat Completions 兼容）；API key 不在这里，按 `id` 存在 Keychain /
/// An LLM provider the user added (OpenAI Chat Completions compatible); the API key is not stored here but in the Keychain under `id`
@Model
final class LLMProvider {

    /// 唯一标识，也是 Keychain 里取 API key 的 account / The unique ID, also the Keychain account holding the API key
    @Attribute(.unique) private(set) var id = UUID()

    /// 用户起的名字，如 `My OpenRouter` / The name the user gave it, such as `My OpenRouter`
    var name: String = ""

    /// base URL，如 `https://openrouter.ai/api/v1`；请求时去掉结尾的 `/` 再拼 `/chat/completions` /
    /// The base URL, such as `https://openrouter.ai/api/v1`; a trailing `/` is dropped before appending `/chat/completions`
    var baseURL: String = ""

    /// 添加时间，列表按它排序 / When it was added; lists sort by it
    private(set) var createdAt = Date.now

    // MARK: - 关系 / Relationships

    /// 这个服务商下的模型；删服务商时一起删。数组不保证顺序，显示时按 `createdAt` 排 /
    /// The models under this provider, deleted along with it. The array has no guaranteed order; sort by `createdAt` for display
    @Relationship(deleteRule: .cascade, inverse: \LLMModel.provider)
    var models: [LLMModel] = []

    /// 按添加顺序排好的模型，列表显示用它 / The models in the order they were added, for display in lists
    var sortedModels: [LLMModel] {
        let sorted = models.sorted { first, second in
            first.createdAt < second.createdAt
        }
        return sorted
    }

    // MARK: - 初始化 / Init

    init(name: String, baseURL: String) {
        self.name = name
        self.baseURL = baseURL
    }
}
