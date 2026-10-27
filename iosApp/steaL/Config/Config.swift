//
//  Config.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation

enum Config {
    // key 来自 Secrets.xcconfig（不进 git），经 Info.plist 的 $(...) 替换进包里
    /// ITAD API key / ITAD API key
    static let itadAPIKey = infoValue(for: "ITADAPIKey")
    /// LLM 服务商的 API key / The LLM provider's API key
    static let llmAPIKey = infoValue(for: "LLMAPIKey")
    /// LLM 模型名，如 `deepseek-flash` / The LLM model name, such as `deepseek-flash`
    static let llmModel = infoValue(for: "LLMModel")
    /// OpenAI Chat Completions 兼容的 base URL，如 `https://api.deepseek.com` /
    /// An OpenAI Chat Completions compatible base URL, such as `https://api.deepseek.com`
    static let llmAPIURL = infoValue(for: "LLMAPIURL")

    /// 读 Info.plist 里的字符串，缺了返回空串 / Reads a string from Info.plist, empty if missing
    private static func infoValue(for key: String) -> String {
        let value = Bundle.main.object(forInfoDictionaryKey: key) as? String
        return value ?? ""
    }
}
