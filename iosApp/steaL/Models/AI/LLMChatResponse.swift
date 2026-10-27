//
//  LLMChatResponse.swift
//  steaL
//
//  Created by yijiang on 2026/9/25.
//

import Foundation

// MARK: - 根对象 / Root

/// OpenAI Chat Completions 兼容协议的响应体，只解用得到的字段 /
/// Response body of the OpenAI Chat Completions compatible protocol, decoding only the fields in use
///
/// 模型的回答是 `choices[0].message.content` 里的**字符串**；要 JSON 时还得再解一次 /
/// The model's answer is the **string** in `choices[0].message.content`; JSON output needs a second decode
struct LLMChatResponse: Decodable, Hashable, Sendable {

    /// 候选回答；没要多个时只有一条 / Candidate answers; just one unless more are asked for
    let choices: [LLMChatChoice]
}

// MARK: - 候选回答 / Choice

/// 一条候选回答；提到顶层是因为它有 `CodingKeys`，嵌套两层 swiftlint 会报 /
/// One candidate answer; top level because its `CodingKeys` would otherwise nest two deep
struct LLMChatChoice: Decodable, Hashable, Sendable {

    /// 回答的消息 / The answering message
    let message: Message
    /// 停止原因：`stop` 正常结束，`length` 被截断，其余见服务商文档 /
    /// Why it stopped: `stop` finished, `length` was cut off, others per the provider
    let finishReason: String?

    /// JSON 键名与 Swift 属性名的映射 / Maps JSON keys to Swift properties
    enum CodingKeys: String, CodingKey {
        case message
        case finishReason = "finish_reason"
    }

    /// 回答的消息 / The answering message
    struct Message: Decodable, Hashable, Sendable {

        /// 回答正文，可能是 `null`（DeepSeek 文档）；思考模式的推理在另一个字段，这里不解 /
        /// The answer, which can be `null`; a thinking model's reasoning sits in another field, left undecoded
        let content: String?
    }
}
