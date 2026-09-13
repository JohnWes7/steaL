//
//  ITADPricesResponse.swift
//  steaL
//
//  Created by yijiang on 2026/9/13.
//

import Foundation

// MARK: - 根对象 / Root

/// `POST https://api.isthereanydeal.com/games/prices/v3` 的响应体 /
/// Response of the prices endpoint
///
/// 顶层是**数组**且是**稀疏的**：请求 2 个 ID 可能只回 1 条，**没有 deal 的游戏整条不返回**
/// （实测 Portal 2 单查返回空数组）。所以调用方必须按 `id` 匹配，不能按下标对应 /
/// The root is an array and it is **sparse**: asking for two ids can return one entry, and a game
/// with no deals is omitted entirely (Portal 2 returned an empty array when queried alone).
/// Callers must therefore match by `id` rather than by position
typealias ITADPricesResponse = [ITADPriceGame]


// MARK: - 按 id 取条目 / Looking an entry up by id

extension ITADPricesResponse {

    /// 取某个游戏的条目，没有（当前无折扣价）时返回 `nil` /
    /// The entry for one game, `nil` when it has no discounted price right now
    ///
    /// 响应是稀疏数组，**不能按下标取** —— 传 2 个 ID 可能只回 1 条 /
    /// The response is sparse, so never index into it: two requested ids can come back as one entry
    func entry(for uuid: String) -> ITADPriceGame? {
        first { $0.id == uuid }
    }
}

// MARK: - 单个游戏 / One game

/// 一个游戏的价格总览：历史低价 + 当前全部商店的 deal /
/// One game's price overview: the historical lows plus every shop's current deal
///
/// 与 `/deals/v2` 的关键区别：那边每个游戏只给**一个** deal（ITAD 选出的最佳价），
/// 这边给**全部**商店的 deal，正好用来做跨店比价 /
/// The key difference from `/deals/v2`: that endpoint returns a single best deal per game, while
/// this one returns every shop's deal, which is what a comparison list needs
struct ITADPriceGame: Codable, Hashable, Identifiable, Sendable {

    /// ITAD 游戏 UUID / ITAD game UUID
    let id: String

    /// 历史低价，按时间窗口分档 / Historical lows, split by time window
    let historyLow: ITADPriceHistory?

    /// 各商店当前的优惠，按价格从低到高（实测）/ Current deals per shop, cheapest first
    ///
    /// **可能为空数组**（接口带 `deals=true` 时只返回有折扣的价格）/
    /// **Can be an empty array**; with `deals=true` only discounted prices come back
    let deals: [ITADPriceDeal]?

    /// 全史最低价，供 UI 直接取 / The all-time low, ready for the UI
    var allTimeLow: ITADPrice? { historyLow?.all }

    /// 最便宜的那条 deal / The cheapest deal
    var bestDeal: ITADPriceDeal? { deals?.min { $0.price.amount < $1.price.amount } }

    /// 明确在 Steam 激活的 deal / Deals whose keys explicitly activate on Steam
    var steamDeals: [ITADPriceDeal] { (deals ?? []).filter(\.isSteamActivation) }

    /// 最便宜的 Steam 激活 deal / The cheapest deal that activates on Steam
    var bestSteamDeal: ITADPriceDeal? { steamDeals.min { $0.price.amount < $1.price.amount } }

    /// 是否完全查不到价格 / Whether nothing usable came back
    var isEmpty: Bool { (deals ?? []).isEmpty && allTimeLow == nil }
}

// MARK: - 历史低价 / Historical lows

/// 历史低价的三档窗口 / The three historical-low windows
///
/// ⚠️ 与 `/deals/v2` 里 `<Deal>` 的**扁平** `historyLow` 不同构（那边是三个平铺字段），
/// 所以复用不到一起 / Structurally different from the flat history-low fields inside
/// `/deals/v2`'s deal object, so the two cannot share a type
struct ITADPriceHistory: Codable, Hashable, Sendable {

    /// 全史最低价 / All-time low
    ///
    /// 服务端 schema 标为可空，实测有值时拿得到；没有价格历史的游戏可能为 `nil` /
    /// Marked nullable in the schema; games without any price history can have `nil`
    let all: ITADPrice?

    /// 近一年最低价 / Lowest price within the last year
    let y1: ITADPrice?

    /// 近三个月最低价 / Lowest price within the last three months
    let m3: ITADPrice?

    /// JSON 键名与 Swift 属性名的映射，`y1` / `m3` 无法从属性名还原 /
    /// Maps JSON keys to Swift properties; `y1` and `m3` do not map by name
    enum CodingKeys: String, CodingKey {
        case all
        case y1 = "y1"
        case m3 = "m3"
    }
}

// MARK: - 单条优惠 / One deal

/// 某个商店当前的一条优惠 / One shop's current deal
///
/// 字段与 `/deals/v2` 的 deal 基本一致，但**没有** `historyLow` / `historyLow_1y` /
/// `historyLow_3m` 三个字段 —— 这个接口把历史低价挪到了游戏层级（`ITADPriceGame.historyLow`）/
/// Shares most fields with `/deals/v2`'s deal, but **lacks** the three `historyLow*` fields,
/// which this endpoint lifts up to the game level instead
struct ITADPriceDeal: Codable, Hashable, Sendable {

    /// 购买商店 / Where the key is bought from
    let shop: ITADShopRef

    /// 当前价格 / Current price
    let price: ITADPrice

    /// 原价 / Regular (non-discounted) price
    let regular: ITADPrice

    /// 折扣百分比，`14` 表示 -14% / Discount in percent, e.g. `14` means -14%
    let cut: Int

    /// 优惠券码，通常为 `nil` / Voucher code, usually `nil`
    let voucher: String?

    /// 该商店历史最低价 / Lowest price ever seen in this shop
    let storeLow: ITADPrice?

    /// ITAD 标记如 `"S"`（商店最低价）、`"H"`（历史最低价），可能为 `nil` /
    /// ITAD flag such as `"S"` (store low) or `"H"` (historical low); often `nil`
    let flag: String?

    /// 激活平台，注意不是购买商店 / Activation platforms, not the shop the key comes from
    let drm: [ITADPlatformRef]?

    /// 支持的操作系统 / Supported operating systems
    let platforms: [ITADPlatformRef]?

    /// 价格采集时间，ISO8601 字符串 / ISO8601 string of when the price was seen
    let timestamp: String

    /// 优惠结束时间，`nil` 表示无已知截止时间 / ISO8601 string, `nil` when there is no known expiry
    let expiry: String?

    /// ITAD 跳转链接 / ITAD redirect link
    let url: String

    /// 防御性检查：该 Key 是否在 Steam（`61`）激活 / Defensive check that the key activates on Steam (`61`)
    var isSteamActivation: Bool { (drm ?? []).contains { $0.id == 61 } }

    /// ITAD 的 `"H"` 标记：这条 deal 触达了全史最低价 / ITAD's `"H"` flag: this deal hit an all-time low
    var isHistoricalLow: Bool { flag == "H" }
}
