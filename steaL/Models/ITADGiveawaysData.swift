//
//  ITADGiveawaysData.swift
//  steaL
//
//  Created by yijiang on 2026/9/11.
//

import Foundation

// MARK: - 根对象 / Root

/// `GET https://api.isthereanydeal.com/giveaways/v1` 的响应体 /
/// Response of the giveaways endpoint
///
/// 顶层直接是数组，没有 `nextOffset` / `hasMore` 分页包装，翻页靠递增 `offset` /
/// The top level is a plain array with no `nextOffset` / `hasMore` wrapper, so paging relies on an increasing `offset`
typealias ITADGiveawaysData = [ITADGiveaway]

// MARK: - 列表项 / Giveaway item

/// 一条限时免费领取信息 / One limited-time giveaway
struct ITADGiveaway: Codable, Hashable, Identifiable, Sendable {
    /// ITAD giveaway ID，不是游戏 UUID / ITAD giveaway id, not a game UUID
    let id: Int
    /// 展示标题，如 `"Astral Ascent - FREE on Epic Games"` / Display title, e.g. `"Astral Ascent - FREE on Epic Games"`
    let title: String
    /// 提供领取的商店；`nil` 表示无对应商店（如游戏内兑换码活动） /
    /// Shop offering the giveaway; `nil` when no shop is involved (e.g. in-game code promotions)
    let shop: ITADShopRef?
    /// 领取跳转链接，含 affiliate 参数，必须原样打开 / Claim link with affiliate params, must be opened as-is
    let url: String
    /// ITAD 上的 giveaway 详情页 / Giveaway page on ITAD
    let details: String
    /// 是否限制级内容 / Whether the giveaway is marked mature
    let isMature: Bool
    /// 开始时间，ISO8601 字符串 / ISO8601 string of when the giveaway started
    let publish: String
    /// 领取截止时间，ISO8601 字符串，`nil` 表示截止时间未知 / ISO8601 string, `nil` when the expiry is unknown
    let expiry: String?
    /// 运营备注，可能含 Markdown 与换行符，通常为 `nil` / Editorial note, may contain Markdown and line breaks, usually `nil`
    let note: String?
    /// 该 giveaway 包含的游戏，可能不止一个 / Games included in the giveaway, possibly more than one
    let games: [ITADGiveawayGame]
}

// MARK: - 游戏 / Game

/// giveaway 内包含的单个游戏 / One game included in a giveaway
///
/// 同样的结构也由 `GET /games/giveaways/v1` 返回 /
/// The same shape is also returned by `GET /games/giveaways/v1`
struct ITADGiveawayGame: Codable, Hashable, Identifiable, Sendable {
    /// ITAD 游戏 UUID / ITAD game UUID
    let id: String
    /// 可读的短标识，如 `"astral-ascent"` / Human readable slug, e.g. `"astral-ascent"`
    let slug: String
    /// 游戏标题 / Game title
    let title: String
    /// 条目类型，如 `"game"`、`"dlc"` / Item type, e.g. `"game"`, `"dlc"`
    let type: String
    /// 是否限制级内容 / Whether the game is marked mature
    let mature: Bool
    /// 图片资源 / Image assets
    let assets: ITADAssets
    /// 是否无 DRM / Whether the game is DRM free
    let drmFree: Bool
    /// 可激活该游戏的商店，可能为空数组 / Shops where the game can be activated, possibly empty
    let keys: [ITADShopRef]
    /// 支持的操作系统 / Supported operating systems
    let platforms: [ITADPlatformRef]
}

// MARK: - 派生属性 / Derived properties

extension ITADGiveaway {

    /// 卡片标题：优先第一件游戏的正式名称，兜底整个 giveaway 的标题 /
    /// Card title: prefers the first game's real name, falls back to the giveaway title
    var displayTitle: String {
        games.first?.title ?? title
    }

    /// 行式缩略图：优先小尺寸横幅，最后才回落到竖版封面；无图时为 nil /
    /// Row thumbnail: small banners first, portrait box art only as a last resort; nil without artwork
    var thumbnailURL: URL? {
        guard let assets = games.first?.assets else { return nil }
        return [assets.banner300, assets.banner400, assets.banner145, assets.banner600, assets.boxart]
            .compactMap { $0 }
            .compactMap { URL(string: $0) }
            .first
    }

    /// 领取截止时间；`expiry` 缺失或无法解析时为 nil /
    /// Claim deadline; nil when `expiry` is missing or cannot be parsed
    var expiryDate: Date? {
        guard let expiry else { return nil }
        return Self.iso8601Formatter.date(from: expiry)
    }

    /// 解析 ITAD ISO8601 时间的共享 formatter / Shared formatter for ITAD's ISO8601 strings
    private static let iso8601Formatter = ISO8601DateFormatter()
}
