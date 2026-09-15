//
//  ITADGameLookupResponse.swift
//  steaL
//
//  Created by yijiang on 2026/9/13.
//

import Foundation

// MARK: - 根对象 / Root

/// `GET https://api.isthereanydeal.com/games/lookup/v1?appid=<appID>` 的完整响应体 /
/// Complete response from the ITAD game lookup endpoint
struct ITADGameLookupResponse: Codable, Hashable, Sendable {

    /// 是否找到匹配的 ITAD 游戏 / Whether a matching ITAD game was found
    let found: Bool

    /// 匹配的游戏资料；找不到时为 `nil` / Matched game data; `nil` when no match exists
    let game: ITADLookupGame?
}

/// ITAD lookup 返回的游戏资料 / Game data returned by the ITAD lookup endpoint
struct ITADLookupGame: Codable, Hashable, Sendable {

    /// ITAD 游戏 UUID / ITAD game UUID
    let id: String

    /// 可读短标识 / Human readable slug
    let slug: String?

    /// ITAD 标题 / ITAD title
    let title: String?

    /// 条目类型，例如 `game` / Item type, such as `game`
    let type: String?
}
