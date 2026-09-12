//
//  ITADGameInfoResponse.swift
//  steaL
//
//  Created by yijiang on 2026/9/13.
//

import Foundation

// MARK: - 根对象 / Root

/// `GET https://api.isthereanydeal.com/games/info/v2?id=<uuid>` 的响应体 /
/// Response of the game info endpoint
///
/// 详情页用它补 Steam 没有的东西：**评分来源聚合**（Steam 好评率 / Metascore / OpenCritic）、
/// 标签、热度与厂商。未发售的游戏也能查到，只是 `releaseDate` 为空 /
/// The detail page uses it for what Steam does not provide: aggregated **review sources**
/// (Steam / Metascore / OpenCritic), tags, popularity and credits. Unreleased games are covered
/// too; their `releaseDate` is simply `null`
///
/// 真实响应示例 / A real response:
///
/// ```json
/// {
///   "id": "018d937f-590c-728b-ac35-38bcff85f086",
///   "title": "Elden Ring",
///   "appid": 1245620,
///   "releaseDate": "2022-02-24",
///   "tags": ["Souls-like", "Open World"],
///   "reviews": [{ "score": 93, "source": "Steam", "count": 830941, "url": "..." }],
///   "stats": { "rank": 3, "waitlisted": 39860, "collected": 31572 },
///   "players": { "recent": 45497, "day": 51345, "week": 54240, "peak": 940453 }
/// }
/// ```
struct ITADGameInfoResponse: Codable, Hashable, Identifiable, Sendable {

    // MARK: 标识 / Identity

    /// ITAD 游戏 UUID / ITAD game UUID
    let id: String
    /// 可读短标识 / Human readable slug
    let slug: String
    /// 游戏标题 / Game title
    let title: String
    /// 条目类型 / Item type
    let type: String
    /// 是否限制级内容 / Whether the item is marked mature
    let mature: Bool
    /// Steam 应用 ID，比走 lookup 快一步 / Steam application ID, one hop cheaper than a lookup
    let appid: Int?

    // MARK: 介绍 / Description

    /// 标签，如 `"Souls-like"` / Tags such as `"Souls-like"`
    let tags: [String]?
    /// 图片资源，见 `init(from:)`：同一个接口会返回两种形态 /
    /// Image assets; see `init(from:)`, this field arrives in two different shapes
    let assets: ITADAssets?
    /// 是否抢先体验 / Whether the game is in early access
    let earlyAccess: Bool?
    /// 是否有成就 / Whether the game has achievements
    let achievements: Bool?
    /// 是否有集换式卡牌 / Whether the game has trading cards
    let tradingCards: Bool?

    // MARK: 发行 / Release

    /// 发售日期，**未发售游戏为 `nil`** /
    /// Release date, **`nil` for unreleased games**
    let releaseDate: String?

    // MARK: 分类与厂商 / Classification and credits

    /// 开发商 / Developers
    let developers: [ITADCompanyRef]?
    /// 发行商 / Publishers
    let publishers: [ITADCompanyRef]?

    // MARK: 评分与热度 / Reviews and popularity

    /// 评分来源聚合，未发售游戏为空数组 /
    /// Aggregated review sources; an empty array for unreleased games
    let reviews: [ITADReview]?
    /// 热度排名 / Popularity stats
    let stats: ITADGameStats?
    /// 玩家人数 / Player counts
    let players: ITADPlayerCounts?

    /// 游戏页链接 / The game's page link
    let urls: ITADGameURLs?

    /// `assets` 是**对象与空数组两种形态**，实测与「有没有图片」一一对应：
    /// 有图的游戏给对象（Portal 2 / PRAGMATA），没图的给 `[]`（ELDEN RING）。
    /// 空字典被序列化成数组是服务端的遗留行为，所以 `[]` 视为「没有图片」而非异常；
    /// 但其它形态必须留下痕迹，不能静默变成 `nil` /
    /// `assets` comes in **two shapes that map exactly onto "has artwork or not"**: games with
    /// artwork return an object (Portal 2 / PRAGMATA) while games without return `[]`
    /// (ELDEN RING). The empty-dictionary-as-array is a server-side legacy behaviour, so `[]`
    /// means "no artwork" rather than an error — but any other shape must leave a trace instead
    /// of silently becoming `nil`
    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        id = try container.decode(String.self, forKey: .id)
        slug = try container.decode(String.self, forKey: .slug)
        title = try container.decode(String.self, forKey: .title)
        type = try container.decode(String.self, forKey: .type)
        mature = try container.decode(Bool.self, forKey: .mature)
        appid = try container.decodeIfPresent(Int.self, forKey: .appid)

        tags = try container.decodeIfPresent([String].self, forKey: .tags)

        do {
            assets = try container.decodeIfPresent(ITADAssets.self, forKey: .assets)
        } catch {
            #if DEBUG
            // 已知的数组形态不会走到这里（它在上面按 nil 处理），走到这里说明形态变了 /
            // The known array shape does not land here (it is handled as nil above); landing here
            // means the shape changed
            print("[ITAD] assets 形态未知，已按无图片处理 / unknown assets shape: \(error)")
            #endif
            assets = nil
        }
        earlyAccess = try container.decodeIfPresent(Bool.self, forKey: .earlyAccess)
        achievements = try container.decodeIfPresent(Bool.self, forKey: .achievements)
        tradingCards = try container.decodeIfPresent(Bool.self, forKey: .tradingCards)

        releaseDate = try container.decodeIfPresent(String.self, forKey: .releaseDate)

        developers = try container.decodeIfPresent([ITADCompanyRef].self, forKey: .developers)
        publishers = try container.decodeIfPresent([ITADCompanyRef].self, forKey: .publishers)

        reviews = try container.decodeIfPresent([ITADReview].self, forKey: .reviews)
        stats = try container.decodeIfPresent(ITADGameStats.self, forKey: .stats)
        players = try container.decodeIfPresent(ITADPlayerCounts.self, forKey: .players)

        urls = try container.decodeIfPresent(ITADGameURLs.self, forKey: .urls)
    }

    /// JSON 键名与 Swift 属性名的映射 / Maps JSON keys to Swift properties
    enum CodingKeys: String, CodingKey {
        case id, slug, title, type, mature, appid, tags, assets
        case earlyAccess, achievements, tradingCards
        case releaseDate, developers, publishers, reviews, stats, players, urls
    }
}

// MARK: - 嵌套结构 / Nested types

/// 厂商引用（开发商 / 发行商）/ A company reference (developer or publisher)
struct ITADCompanyRef: Codable, Hashable, Identifiable, Sendable {

    /// 厂商 ID / Company id
    let id: Int
    /// 厂商名称 / Company name
    let name: String
}

/// 一条评分聚合 / One aggregated review source
///
/// 一条请求就把 Steam 好评率、Metascore、Metacritic 用户分和 OpenCritic 拿全 /
/// A single request covers Steam's rating plus Metascore, Metacritic user score and OpenCritic
struct ITADReview: Codable, Hashable, Sendable {

    /// 分数，Steam 是好评百分比 / Score; for Steam it is the positive-rating percentage
    let score: Int?
    /// 来源名，如 `"Steam"`、`"Metascore"`、`"Metacritic User Score"`、`"OpenCritic"` /
    /// Source name, e.g. `"Steam"`, `"Metascore"`, `"Metacritic User Score"`, `"OpenCritic"`
    let source: String?
    /// 参与人数 / Number of ratings behind the score
    let count: Int?
    /// 来源链接 / Link to the source
    let url: String?
}

/// 热度统计 / Popularity stats
struct ITADGameStats: Codable, Hashable, Sendable {

    /// 热度排名 / Popularity rank
    let rank: Int?
    /// 加入期待列表的人数 / How many users waitlisted the game
    let waitlisted: Int?
    /// 加入收藏的人数 / How many users collected the game
    let collected: Int?
}

/// 玩家人数 / Player counts
///
/// 未发售游戏四个值都是 `0`，不是 `nil` /
/// All four are `0` rather than `nil` for unreleased games
struct ITADPlayerCounts: Codable, Hashable, Sendable {

    /// 最近在线 / Most recent count
    let recent: Int?
    /// 过去一天 / Past day
    let day: Int?
    /// 过去一周 / Past week
    let week: Int?
    /// 历史峰值 / All-time peak
    let peak: Int?
}

/// 游戏页链接 / The game's page links
struct ITADGameURLs: Codable, Hashable, Sendable {

    /// ITAD 游戏页 / The game's page on IsThereAnyDeal
    let game: String?
}

// MARK: - 派生属性 / Derived properties

extension ITADGameInfoResponse {

    /// 是否还没发售：这个接口里没有比价数据时 `releaseDate` 就是 `nil` /
    /// Whether the game is unreleased: this endpoint leaves `releaseDate` `nil` until it ships
    var isUnreleased: Bool { releaseDate == nil }

    /// Steam 好评率那一条 / The Steam rating entry
    var steamReview: ITADReview? { reviews?.first { $0.source == "Steam" } }

    /// 主开发商名称，数组第一个 / The primary developer name, first of the list
    var primaryDeveloper: String? { developers?.first?.name }

    /// 主发行商名称，数组第一个 / The primary publisher name, first of the list
    var primaryPublisher: String? { publishers?.first?.name }
}
