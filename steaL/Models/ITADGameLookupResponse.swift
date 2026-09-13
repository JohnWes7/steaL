//
//  ITADGameLookupResponse.swift
//  steaL
//
//  Created by yijiang on 2026/9/13.
//

import Foundation

// MARK: - 根对象 / Root

/// `GET https://api.isthereanydeal.com/games/lookup/v1?appid=<appID>` 的响应体 /
/// Response of the ITAD game lookup endpoint
struct ITADGameLookupResponse: Codable, Hashable, Identifiable, Sendable {

    /// ITAD 游戏 UUID / ITAD game UUID
    let id: String
}
