//
//  LLMChatRequest.swift
//  steaL
//
//  Created by yijiang on 2026/9/25.
//

import Foundation

// MARK: - 根对象 / Root

/// OpenAI Chat Completions 兼容协议的请求体（`POST {base}/chat/completions`），只写用得到的字段 /
/// Request body of the OpenAI Chat Completions compatible protocol, limited to the fields in use
///
/// 故意不传 `max_tokens` / `temperature` / DeepSeek 的 `thinking`：各家支持不一，传错会 400（见 `docs/AI Advisor Planning.md`）/
/// Deliberately omits `max_tokens`, `temperature` and DeepSeek's `thinking`: providers differ, and a wrong one answers 400
struct LLMChatRequest: Encodable, Hashable, Sendable {

    /// 模型名，如 `deepseek-flash` / Model name, such as `deepseek-flash`
    let model: String
    /// 对话消息，按顺序 / The conversation messages, in order
    let messages: [Message]
    /// 输出格式；要求 JSON 时 prompt 里必须出现 "json"（DeepSeek）/
    /// Output format; for JSON the prompt must contain the word "json" (DeepSeek)
    let responseFormat: ResponseFormat

    /// JSON 键名与 Swift 属性名的映射 / Maps JSON keys to Swift properties
    enum CodingKeys: String, CodingKey {
        case model
        case messages
        case responseFormat = "response_format"
    }
}

// MARK: - 嵌套结构 / Nested types

extension LLMChatRequest {

    /// 一条消息 / One message
    struct Message: Encodable, Hashable, Sendable {

        /// 角色 / The speaker
        let role: Role
        /// 正文 / The text
        let content: String
    }

    /// 消息角色 / A message's role
    enum Role: String, Encodable, Hashable, Sendable {
        /// 规则与格式，优先级高于 `user` / Rules and format, ranked above `user`
        case system
        /// 这次要处理的内容 / What to work on this time
        case user
    }

    /// 输出格式 / The output format
    struct ResponseFormat: Encodable, Hashable, Sendable {

        /// `text` 或 `json_object` / `text` or `json_object`
        let type: String

        /// 只输出一个 JSON 对象 / A single JSON object only
        static let jsonObject = ResponseFormat(type: "json_object")
    }
}
