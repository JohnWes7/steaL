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
    /// DeepSeek API key / DeepSeek API key
    static let llmAPIKey = infoValue(for: "LLMAPIKey")
    static let llmModel = ""
    static let llmAPIURL = ""

    /// 读 Info.plist 里的字符串，缺了返回空串 / Reads a string from Info.plist, empty if missing
    private static func infoValue(for key: String) -> String {
        let value = Bundle.main.object(forInfoDictionaryKey: key) as? String
        return value ?? ""
    }
}
