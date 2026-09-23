//
//  SteamAppAvailabilityResponse.swift
//  steaL
//
//  Created by yijiang on 2026/9/23.
//

import Foundation

// MARK: - 根对象 / Root

/// `appdetails?appids=a,b,c&filters=price_overview` 的响应体：每个 appid 只读「存不存在」与「有没有价格」/
/// Response of a batched `appdetails` call filtered to `price_overview`; each appid only yields whether it exists and has a price
///
/// 不复用 `SteamAppDetailsResponse`：过滤后的 `data` 只剩 `price_overview`，免费游戏还是 `[]`，按完整详情解码会失败 /
/// Not `SteamAppDetailsResponse`: the filtered `data` holds only `price_overview` (or `[]` for free games),
/// so decoding it as full details would fail
struct SteamAppAvailabilityResponse: Decodable, Sendable {

    /// appid 字符串 → 查询结果 / appid string to its result
    let entries: [String: SteamAppAvailabilityEntry]

    /// 直接按字典解码根对象 / Decodes the root dictionary as-is
    init(from decoder: any Decoder) throws {
        entries = try [String: SteamAppAvailabilityEntry](from: decoder)
    }

    /// 供空请求与预览直接构造 / Lets empty requests and previews build one directly
    init(entries: [String: SteamAppAvailabilityEntry]) {
        self.entries = entries
    }

    /// 在 Steam 上存在 / Exists on Steam
    func isAvailable(_ appID: Int) -> Bool {
        entries[String(appID)]?.success == true
    }

    /// 存在且有价格 / Exists and has a price
    func hasPrice(_ appID: Int) -> Bool {
        entries[String(appID)].map { $0.success && $0.hasPrice } ?? false
    }
}

// MARK: - 查询结果 / Lookup result

/// 单个 appid 的结果 / One appid's result
struct SteamAppAvailabilityEntry: Decodable, Sendable {

    /// `false` 表示已下架、废弃或锁区 / `false` means delisted, retired or region-locked
    let success: Bool

    /// 有 `price_overview`，即能买（限时免费也有）；demo、无价格的旧页、永久免费都没有 /
    /// Has a `price_overview`, i.e. purchasable (limited-time free counts); demos, price-less legacy pages and free-to-play don't
    let hasPrice: Bool

    private enum CodingKeys: String, CodingKey { case success, data }
    private enum DataKeys: String, CodingKey { case priceOverview = "price_overview" }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        success = try container.decode(Bool.self, forKey: .success)
        // `data` 可能缺失、是对象，也可能是 `[]`；不是对象就取不到键容器，一律当没价格 /
        // `data` may be missing, an object, or `[]`; anything but an object yields no keyed container and counts as no price
        let data = try? container.nestedContainer(keyedBy: DataKeys.self, forKey: .data)
        hasPrice = data?.contains(.priceOverview) ?? false
    }
}
