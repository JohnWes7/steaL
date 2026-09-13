//
//  SteamSearchResponse.swift
//  steaL
//
//  Created by yijiang on 2026/9/13.
//

import Foundation

// MARK: - 根对象 / Root

/// `https://store.steampowered.com/api/storesearch` 的响应体 /
/// Response from Steam's `storesearch` endpoint
struct SteamSearchResponse: Codable, Hashable, Sendable {

    /// 符合搜索词的条目总数 / Total number of matching store items
    let total: Int

    /// 当前响应返回的搜索条目 / Search items returned in this response
    let items: [SteamSearchItem]

    /// 不发网络请求时使用的空结果 / Empty result used when no request is needed
    static let empty = SteamSearchResponse(total: 0, items: [])
}

// MARK: - 搜索条目 / Search item

/// 搜索列表所需的轻量 Steam 商店条目；完整资料仍由 `appdetails` 获取 /
/// A lightweight Steam store item for search lists; full details still come from `appdetails`
struct SteamSearchItem: Codable, Hashable, Sendable, Identifiable {

    /// 条目类型，通常为 `"app"` / Store item type, normally `"app"`
    let type: String

    /// 本地化名称 / Localised name
    let name: String

    /// Steam App ID / Steam application ID
    let id: Int

    /// 当前地区价格；免费、未发售或不可购买时可能缺失 /
    /// Regional price; may be absent for free, unreleased, or unavailable items
    let price: SteamSearchPrice?

    /// 搜索结果横图 / Search-result capsule image
    let tinyImage: String?

    /// Metacritic 分数；Steam 会把空值和数字都编码成字符串 /
    /// Metacritic score; Steam encodes both an empty value and a score as strings
    let metascore: String?

    /// 支持平台 / Supported platforms
    let platforms: SteamPlatforms?

    /// 手柄支持级别，例如 `"full"` / Controller support level, such as `"full"`
    let controllerSupport: String?

    enum CodingKeys: String, CodingKey {
        case type, name, id, price, metascore, platforms
        case tinyImage = "tiny_image"
        case controllerSupport = "controller_support"
    }
}

/// 搜索结果里的价格，金额单位为最小货币单位（例如 cents）/
/// Price returned by search, expressed in the currency's minor unit (for example cents)
struct SteamSearchPrice: Codable, Hashable, Sendable {

    /// ISO 4217 货币代码 / ISO 4217 currency code
    let currency: String

    /// 原价 / Original price
    let initial: Int

    /// 当前价 / Current price
    let final: Int

    /// 是否正在打折 / Whether the item is currently discounted
    var isDiscounted: Bool { final < initial }
}