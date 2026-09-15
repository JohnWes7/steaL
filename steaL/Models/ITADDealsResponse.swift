//
//  ITADDealsResponse.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import Foundation

// MARK: - 根对象 / Root

/// `POST https://api.isthereanydeal.com/deals/v2` 的响应体 / Response of the deals endpoint
struct ITADDealsResponse: Codable, Hashable, Sendable {
    /// 下次请求使用的偏移量，以服务端返回值为准 / Offset to pass into the next request, as reported by the server
    let nextOffset: Int?
    /// 是否还有下一页 / Whether more pages are available
    let hasMore: Bool
    /// 本页的优惠列表 / Deals on the current page
    let list: [ITADDeal]

    /// 当前页是否已经是最后一页 / `true` when the current page already contains every result
    var isEnd: Bool { !hasMore }
}

// MARK: - 列表项 / Deal item

/// 单个游戏及其当前最佳优惠 / One game with its current best deal
struct ITADDeal: Codable, Hashable, Identifiable, Sendable {
    /// ITAD 内部 ID，UUID 形状，可直接用于 `ForEach` / ITAD internal id, UUID-shaped, safe for `ForEach`
    let id: String
    /// 可读的短标识，如 `"hard-reset-redux"` / Human readable slug, e.g. `"hard-reset-redux"`
    let slug: String
    /// 游戏标题 / Game title
    let title: String
    /// 条目类型：`"game"`、`"dlc"`、`"package"`、`"software"`、`"hardware"` / Item type
    let type: String
    /// 是否限制级内容 / Whether the item is marked mature
    let mature: Bool
    /// 图片资源 / Image assets
    let assets: ITADAssets
    /// 当前优惠详情 / Details of the current deal
    let deal: ITADDealInfo
}

// MARK: - 图片资源 / Assets

/// 各类封面与横幅图片地址 / Cover and banner image URLs
struct ITADAssets: Codable, Hashable, Sendable {
    /// 竖版封面 / Vertical box art
    let boxart: String?
    /// 145 宽横幅 / 145px wide banner
    let banner145: String?
    /// 300 宽横幅 / 300px wide banner
    let banner300: String?
    /// 400 宽横幅 / 400px wide banner
    let banner400: String?
    /// 600 宽横幅 / 600px wide banner
    let banner600: String?
}

// MARK: - 优惠详情 / Deal detail

/// 每个游戏只会返回一个 `deal`，即 ITAD 选出的最佳价格 / One game returns a single `deal` — the best price ITAD picked for it
struct ITADDealInfo: Codable, Hashable, Sendable {
    /// 购买商店，如 Steam、Fanatical、Humble Store / Where the key is bought from
    let shop: ITADShopRef
    /// 当前价格 / Current price
    let price: ITADPrice
    /// 原价 / Regular (non-discounted) price
    let regular: ITADPrice
    /// 折扣百分比，`30` 表示 -30% / Discount in percent, e.g. `30` means -30%
    let cut: Int
    /// 优惠券码，通常为 `nil` / Voucher code, usually `nil`
    let voucher: String?
    /// 该商店历史最低价 / Lowest price ever seen in this shop
    let storeLow: ITADPrice?
    /// 全史最低价 / All-time historical low
    let historyLow: ITADPrice?
    /// 近一年最低价 / Historical low within the last year
    let historyLow1y: ITADPrice?
    /// 近三个月最低价 / Historical low within the last three months
    let historyLow3m: ITADPrice?
    /// ITAD 简写标记，`"S"` 表示商店最低价、`"H"` 表示历史最低价、`"N"` 表示新的历史最低价 / ITAD shorthand flag: `"S"` store low, `"H"` historical low, `"N"` new historical low
    let flag: String?
    /// 激活平台，注意不是购买商店 / Activation platforms, not the shop the key is bought from
    let drm: [ITADPlatformRef]
    /// 支持的操作系统 / Supported operating systems
    let platforms: [ITADPlatformRef]
    /// 价格采集时间，ISO8601 字符串，如 `"2026-09-01T19:15:45+02:00"` / ISO8601 string of when the price was seen
    let timestamp: String
    /// 优惠结束时间，ISO8601 字符串，`nil` 表示无已知截止时间 / ISO8601 string, `nil` when there is no known expiry
    let expiry: String?
    /// ITAD 跳转链接 / ITAD redirect link
    let url: String

    /// JSON 键名与 Swift 属性名的映射，`historyLow_1y`、`historyLow_3m` 含下划线需手动指定 /
    /// Maps JSON keys to Swift properties; the two `historyLow_*` keys need explicit names
    enum CodingKeys: String, CodingKey {
        case shop, price, regular, cut, voucher, storeLow, historyLow, flag
        case drm, platforms, timestamp, expiry, url
        case historyLow1y = "historyLow_1y"
        case historyLow3m = "historyLow_3m"
    }

    /// 防御性检查：该 Key 是否在 Steam（`61`）激活 / Defensive check that the key activates on Steam (`61`)
    var isSteamActivation: Bool {
        drm.contains { $0.id == 61 }
    }

    /// 当前价是否等于或低于全史最低价 / `true` when the current price matches or beats the all-time low
    var isHistoricalLow: Bool {
        guard let historyLow else { return false }
        return price.amountInt <= historyLow.amountInt
    }
}

// MARK: - 值类型 / Value types

/// 商店引用（购买来源）/ Shop reference (where the key is bought)
struct ITADShopRef: Codable, Hashable, Identifiable, Sendable {
    /// 商店 ID，`61` 为 Steam / Shop id, `61` is Steam
    let id: Int
    /// 商店名称 / Shop name
    let name: String
}

/// 平台引用（DRM 或操作系统）/ Platform reference (DRM or operating system)
struct ITADPlatformRef: Codable, Hashable, Identifiable, Sendable {
    /// 平台 ID，`1` 为 Windows、`2` 为 Mac / Platform id, `1` Windows, `2` Mac
    let id: Int
    /// 平台名称 / Platform name
    let name: String
}

/// 金额对象 / Money value
struct ITADPrice: Codable, Hashable, Sendable {
    /// 以主币单位表示的价格，如 `11.37` / Price in major currency units, e.g. `11.37`
    let amount: Double
    /// 以最小货币单位表示的价格，如 `1137` 表示 11.37 AUD / Price in the smallest currency unit, e.g. `1137` for 11.37 AUD
    let amountInt: Int
    /// ISO 4217 货币代码，如 `"AUD"` / ISO 4217 code, e.g. `"AUD"`
    let currency: String
}

// MARK: - 选图 / Artwork selection

extension ITADAssets {

    /// 按给定顺序取第一个能解析成 URL 的字符串 /
    /// Returns the first entry that parses into a URL, following the given order
    fileprivate func firstURL(from candidates: [String?]) -> URL? {
        candidates
            .compactMap { $0 }
            .compactMap { URL(string: $0) }
            .first
    }
}

/// 不同卡片形状要不同方向的图，挑选顺序集中在这里，避免每个卡片视图各写一份回退链 /
/// Different card shapes need different orientations, so the pick order lives in one place
extension ITADDeal {

    /// 宽幅图，供 hero 卡这类横向卡片使用：优先 banner，最后才回落到竖版封面 /
    /// Wide artwork for landscape cards such as the hero: banners first, box art only as a last resort
    var wideArtworkURL: URL? {
        assets.firstURL(from: [
            assets.banner600,
            assets.banner400,
            assets.banner300,
            assets.banner145,
            assets.boxart,
        ])
    }

    /// 竖版封面，供网格卡这类竖向卡片使用：优先 boxart，缺失时才回落到横幅 /
    /// Portrait artwork for vertical cards such as the grid: box art first, banners only as a fallback
    var portraitArtworkURL: URL? {
        assets.firstURL(from: [
            assets.boxart,
            assets.banner600,
            assets.banner400,
            assets.banner300,
            assets.banner145,
        ])
    }
}

