//
//  Logger+App.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import OSLog

// 用系统统一日志代替 `print`：Xcode 控制台可按分类 / 级别筛选、点日志跳到代码行（WWDC20「Explore logging in Swift」）/
// Unified logging instead of `print`: the Xcode console filters by category and level and jumps to the source line
/// 各模块的日志分类 / Log categories for each part of the app
extension Logger {

    /// 所有分类共用的 subsystem，取 bundle ID / The subsystem every category shares: the bundle ID
    nonisolated private static let subsystem = Bundle.main.bundleIdentifier ?? "steaL"

    // `nonisolated`：Service 与 DTO 解码也要能用，`Logger` 是 Sendable /
    // `nonisolated` so services and DTO decoding can use them; `Logger` is Sendable

    /// 启动与本地设置 / Launch and local settings
    nonisolated static let settings = Logger(subsystem: subsystem, category: "Settings")
    /// Steam 请求 / Steam requests
    nonisolated static let steam = Logger(subsystem: subsystem, category: "Steam")
    /// ITAD 请求与解码 / ITAD requests and decoding
    nonisolated static let itad = Logger(subsystem: subsystem, category: "ITAD")
    /// LLM 请求 / LLM requests
    nonisolated static let llm = Logger(subsystem: subsystem, category: "LLM")
    /// 首页 / The home page
    nonisolated static let games = Logger(subsystem: subsystem, category: "Games")
    /// 详情页 / The detail page
    nonisolated static let gameDetail = Logger(subsystem: subsystem, category: "GameDetail")
    /// Wishlist 页 / The wishlist page
    nonisolated static let wishlist = Logger(subsystem: subsystem, category: "Wishlist")
}
