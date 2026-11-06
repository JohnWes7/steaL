//
//  Config.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation

/// App 的配置值：第三方 key、LLM 地址与自建后端地址。
/// The app's configuration values: third-party keys, the LLM endpoint, and the address of our own backend.
///
/// key 来自 Secrets.xcconfig（不进 git），经 Info.plist 的 `$(...)` 替换进包里；后端地址不是秘密，直接写在这里。
/// Keys come from Secrets.xcconfig (kept out of git) and reach the bundle through `$(...)` in Info.plist;
/// the backend address is not a secret, so it is written here directly.
enum Config {
    /// ITAD API key。
    /// The ITAD API key.
    static let itadAPIKey = infoValue(for: "ITADAPIKey")

    /// LLM 服务商的 API key。
    /// The LLM provider's API key.
    static let llmAPIKey = infoValue(for: "LLMAPIKey")

    /// LLM 模型名，如 `deepseek-flash`。
    /// The LLM model name, such as `deepseek-flash`.
    static let llmModel = infoValue(for: "LLMModel")

    /// OpenAI Chat Completions 兼容的 base URL，如 `https://api.deepseek.com`。
    /// An OpenAI Chat Completions compatible base URL, such as `https://api.deepseek.com`.
    static let llmAPIURL = infoValue(for: "LLMAPIURL")

    /// 自建后端的 base URL（含 `/v1`），同 `api/openapi.yaml` 的 `servers`。
    /// The base URL of our own backend (including `/v1`), the same as `servers` in `api/openapi.yaml`.
    ///
    /// 现在是 AWS 香港 API Gateway 的默认地址，还没有自定义域名；换域名时只改这里。
    /// This is the default API Gateway address in AWS Hong Kong, with no custom domain yet; only this changes when one is added.
    static let backendBaseURL = URL(string: "https://1gfwpsgqnc.execute-api.ap-east-1.amazonaws.com/v1")!

    /// 读 Info.plist 里的字符串，缺了返回空串。
    /// Reads a string from Info.plist, or an empty string if it is missing.
    private static func infoValue(for key: String) -> String {
        let value = Bundle.main.object(forInfoDictionaryKey: key) as? String
        return value ?? ""
    }
}
