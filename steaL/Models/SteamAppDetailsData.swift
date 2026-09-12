//
//  SteamAppDetails.swift
//  steaL
//
//  Created by yijiang on 2026/9/13.
//

import Foundation

// MARK: - 根对象 / Root

/// `https://store.steampowered.com/api/appdetails` 的响应体 /
/// Response of the Steam store `appdetails` endpoint
///
/// 顶层是**以 appid 为 key 的字典**（即使只查一个 app 也是字典），值里再分 `success` 与 `data` /
/// The root is a **dictionary keyed by appid** even for a single lookup; each value carries
/// `success` and `data`
///
/// 真实响应示例 / A real response:
///
/// ```json
/// {
///   "1245620": {
///     "success": true,
///     "data": { "type": "game", "name": "ELDEN RING", "is_free": false, "...": "..." }
///   },
///   "9999999": { "success": false }
/// }
/// ```
struct SteamAppDetailsResponse: Codable, Hashable, Sendable {

    /// appid 字符串 → 查询结果 / appid string to its lookup result
    let entries: [String: SteamAppDetailsEntry]

    /// 直接按字典解码根对象 / Decodes the root dictionary as-is
    init(from decoder: any Decoder) throws {
        entries = try [String: SteamAppDetailsEntry](from: decoder)
    }

    /// 供预览与测试直接构造 / Lets previews and tests build one directly
    init(entries: [String: SteamAppDetailsEntry]) {
        self.entries = entries
    }
}

// MARK: - 查询结果 / Lookup result

/// 单个 appid 的查询结果 / The lookup result for one appid
struct SteamAppDetailsEntry: Codable, Hashable, Sendable {

    /// 是否查询成功，`false` 表示游戏在 Steam 上不存在（下架 / 锁区）/
    /// Whether the lookup succeeded; `false` means the game is not on Steam
    let success: Bool

    /// 游戏详情，仅 `success == true` 时存在 / Game details, present only when `success == true`
    let data: SteamAppDetails?

    /// 查询成功且拿到了详情 / `true` when the lookup succeeded and returned details
    var isAvailable: Bool { success && data != nil }
}

// MARK: - 游戏详情 / Game details

/// Steam 游戏详情页的全部数据 / Everything the Steam store page carries for one game
///
/// 字段按详情页的用途分组，除标题与类型外全部可选 —— Steam 对不同游戏返回的字段差异很大 /
/// Fields are grouped by how the detail page uses them; everything except the title and type is
/// optional, because Steam's payload varies a lot between games
struct SteamAppDetails: Codable, Hashable, Sendable {

    // MARK: 标识 / Identity

    /// Steam 应用 ID / Steam application ID
    let steamAppID: Int?
    /// 本地化标题，缺了用列表传过来的名字兜底 / Localised title, falling back to the name handed in by the list
    let name: String?
    /// 条目类型：`"game"`、`"demo"`、`"dlc"`、`"music"` 等 / Item type
    let type: String?
    /// 强制年龄等级，响应里既有数字也有字符串，见 `init(from:)` / Age gate; the payload uses both numbers and strings
    let requiredAge: Int?

    // MARK: 介绍 / Description

    /// 简短描述，纯文本 / Short description, plain text
    let shortDescription: String?
    /// 详细描述，含 `[b]`、`[list]` 等 BBCode 标记 / Detailed description, contains BBCode markup
    let detailedDescription: String?
    /// “关于这款游戏”区块，含 BBCode 与 HTML 标记 / "About this game" block, carries BBCode and HTML
    let aboutTheGame: String?

    // MARK: 媒体 / Media

    /// 竖版主图 / Portrait header image
    let headerImage: String?
    /// 横版宣传图 / Landscape capsule image
    let capsuleImage: String?
    /// 截图，通常 8–25 张 / Screenshots, usually 8–25 of them
    let screenshots: [SteamScreenshot]?
    /// 视频，`hls_h264` 是 `.m3u8`，`AVPlayer` 可直接播放 / Movies; `hls_h264` is an `.m3u8` `AVPlayer` can play as-is
    let movies: [SteamMovie]?

    // MARK: 发行 / Release

    /// 发行日期 / Release date
    let releaseDate: SteamReleaseDate?
    /// 是否免费（免费游戏或 Demo）/ Whether the game is free (F2P title or demo)
    let isFree: Bool?
    /// 基础游戏（仅 Demo 等条目有值），指回本体 / The base game, present for demos and similar
    let fullgame: SteamFullGame?

    // MARK: 价格 / Price

    /// 当前价格与折扣，未发售 / 已下架 / F2P 时为 `nil` / Current price and discount; `nil` before release, after delisting, or for F2P
    let priceOverview: SteamPriceOverview?

    // MARK: 分类与厂商 / Classification and credits

    /// 玩法分类，如 “单人”“多人合作” / Feature categories such as Single-player
    let categories: [SteamDescriptor]?
    /// 类型标签，如 “动作”“角色扮演” / Genre tags such as Action, RPG
    let genres: [SteamDescriptor]?
    /// 支持平台 / Supported platforms
    let platforms: SteamPlatforms?
    /// 开发商 / Developers
    let developers: [String]?
    /// 发行商 / Publishers
    let publishers: [String]?

    /// JSON 键名与 Swift 属性名的映射，下划线字段需手动指定 /
    /// Maps JSON keys to Swift properties; the snake_case keys need explicit names
    enum CodingKeys: String, CodingKey {
        case name, type, movies, screenshots, platforms, developers, publishers
        case categories, genres, fullgame
        case steamAppID = "steam_appid"
        case requiredAge = "required_age"
        case shortDescription = "short_description"
        case detailedDescription = "detailed_description"
        case aboutTheGame = "about_the_game"
        case headerImage = "header_image"
        case capsuleImage = "capsule_image"
        case releaseDate = "release_date"
        case isFree = "is_free"
        case priceOverview = "price_overview"
    }

    /// `required_age` 在实测响应里有两种形态：`0`（数字）与 `"18"`（字符串）。
    /// 用合成的解码会把整个详情页搞挂，所以两种都收 /
    /// `required_age` arrives both as `0` and as `"18"`. The synthesised decoding would fail the
    /// whole payload over it, so both shapes are accepted
    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        name = try container.decodeIfPresent(String.self, forKey: .name)
        type = try container.decodeIfPresent(String.self, forKey: .type)
        steamAppID = try container.decodeIfPresent(Int.self, forKey: .steamAppID)
        requiredAge = container.flexibleInt(forKey: .requiredAge)

        shortDescription = try container.decodeIfPresent(String.self, forKey: .shortDescription)
        detailedDescription = try container.decodeIfPresent(String.self, forKey: .detailedDescription)
        aboutTheGame = try container.decodeIfPresent(String.self, forKey: .aboutTheGame)

        headerImage = try container.decodeIfPresent(String.self, forKey: .headerImage)
        capsuleImage = try container.decodeIfPresent(String.self, forKey: .capsuleImage)
        screenshots = try container.decodeIfPresent([SteamScreenshot].self, forKey: .screenshots)
        movies = try container.decodeIfPresent([SteamMovie].self, forKey: .movies)

        releaseDate = try container.decodeIfPresent(SteamReleaseDate.self, forKey: .releaseDate)
        isFree = try container.decodeIfPresent(Bool.self, forKey: .isFree)
        fullgame = try container.decodeIfPresent(SteamFullGame.self, forKey: .fullgame)

        priceOverview = try container.decodeIfPresent(SteamPriceOverview.self, forKey: .priceOverview)

        categories = try container.decodeIfPresent([SteamDescriptor].self, forKey: .categories)
        genres = try container.decodeIfPresent([SteamDescriptor].self, forKey: .genres)
        platforms = try container.decodeIfPresent(SteamPlatforms.self, forKey: .platforms)
        developers = try container.decodeIfPresent([String].self, forKey: .developers)
        publishers = try container.decodeIfPresent([String].self, forKey: .publishers)
    }
}

// MARK: - 嵌套结构 / Nested types

/// 单张截图 / One screenshot
struct SteamScreenshot: Codable, Hashable, Sendable {

    /// Steam 内部 ID，允许为 `0` / Steam internal id; `0` is a valid value
    let id: Int?
    /// 缩略图 / Thumbnail
    let thumbnail: String?
    /// 原图 / Full-size image
    let full: String?

    /// JSON 键名与 Swift 属性名的映射 / Maps JSON keys to Swift properties
    enum CodingKeys: String, CodingKey {
        case id
        case thumbnail = "path_thumbnail"
        case full = "path_full"
    }
}

/// 单个视频 / One movie
///
/// `hls_h264` 是 HLS 播放列表，`AVPlayer` 原生支持；`webm` 只有浏览器用得上，这里一并留着备用 /
/// `hls_h264` is an HLS playlist that `AVPlayer` handles natively; `webm` is only useful to
/// browsers but is kept for reference
struct SteamMovie: Codable, Hashable, Sendable {

    /// Steam 内部视频 ID，拼封面图会用到 / Steam internal movie id, used to build the poster URL
    let id: Int?
    /// 视频名称，`l=schinese` 时会被本地化 / Movie name, localised when `l=schinese`
    let name: String?
    /// 是否是主预告片 / Whether this is the highlighted trailer
    let highlight: Bool?
    /// 视频缩略图 / Poster image
    let thumbnail: String?
    /// HLS 播放列表地址 / HLS playlist URL
    let hlsH264: String?
    /// DASH 地址（AV1 编码）/ DASH URL, AV1 codec
    let dashAV1: String?
    /// DASH 地址（H.264 编码）/ DASH URL, H.264 codec
    let dashH264: String?

    /// JSON 键名与 Swift 属性名的映射 / Maps JSON keys to Swift properties
    enum CodingKeys: String, CodingKey {
        case id, name, highlight, thumbnail
        case hlsH264 = "hls_h264"
        case dashAV1 = "dash_av1"
        case dashH264 = "dash_h264"
    }
}

/// 发行日期 / Release date
struct SteamReleaseDate: Codable, Hashable, Sendable {

    /// 是否尚未发售，判断未发售**只能**看这个字段，不能靠价格有无倒推 /
    /// Whether the game is unreleased; unreleased games **must** be told by this flag rather than
    /// by the absence of a price
    let comingSoon: Bool
    /// 展示用日期文本，可能是 "2026"、"Q1 2027" 这类非标准格式 / Display text, may be non-standard such as "2026"
    let date: String?

    /// JSON 键名与 Swift 属性名的映射 / Maps JSON keys to Swift properties
    enum CodingKeys: String, CodingKey {
        case date
        case comingSoon = "coming_soon"
    }
}

/// 价格与折扣 / Price and discount
struct SteamPriceOverview: Codable, Hashable, Sendable {

    /// ISO 4217 货币代码，如 `"AUD"` / ISO 4217 currency code
    let currency: String
    /// 原价（分）/ Regular price in the smallest currency unit
    let initial: Int
    /// 折后价（分），无折扣时等于 `initial` / Final price in the smallest currency unit
    let final: Int
    /// 折扣百分比，`0` 表示无折扣 / Discount in percent, `0` when there is none
    let discountPercent: Int
    /// 格式化后的原价，如 `"$99.95"` / Formatted regular price
    let initialFormatted: String?
    /// 格式化后的折后价 / Formatted final price
    let finalFormatted: String?

    /// JSON 键名与 Swift 属性名的映射 / Maps JSON keys to Swift properties
    enum CodingKeys: String, CodingKey {
        case currency, initial, final
        case discountPercent = "discount_percent"
        case initialFormatted = "initial_formatted"
        case finalFormatted = "final_formatted"
    }
}

/// 本体的引用（Demo 条目的 `fullgame`）/ Reference to the base game (a demo's `fullgame`)
struct SteamFullGame: Codable, Hashable, Sendable {

    /// 本体的 appid 字符串 / The base game's appid as a string
    let appid: String?
    /// 本体的名称 / The base game's name
    let name: String?
}

/// 名称型描述项，`categories` 与 `genres` 共用 / A named descriptor, shared by `categories` and `genres`
///
/// 两边的 `id` 类型在响应里**不一致**：`genres` 给字符串（`"1"`）、`categories` 给数字（`2`），
/// 所以这里两种都收，统一转成 `Int`；两边 ID 本就不是同一套命名空间，只用作稳定标识 /
/// The two payloads disagree on `id`: `genres` sends `"1"` while `categories` sends `2`. Both
/// are accepted and normalised to `Int`; the two ID spaces are unrelated, so the value is only
/// used as a stable identifier
struct SteamDescriptor: Codable, Hashable, Sendable {

    /// 描述项 ID / Descriptor id
    let id: Int?
    /// 显示名称 / Display name
    let description: String?

    /// 兼容字符串与数字两种 `id` / Accepts `id` as either a string or a number
    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        id = container.flexibleInt(forKey: .id)
    }

    /// 供预览与测试直接构造 / Lets previews and tests build one directly
    init(id: Int? = nil, description: String? = nil) {
        self.id = id
        self.description = description
    }

    enum CodingKeys: String, CodingKey {
        case id, description
    }
}

/// 支持平台 / Supported platforms
struct SteamPlatforms: Codable, Hashable, Sendable {

    /// 是否支持 Windows / Whether Windows is supported
    let windows: Bool?
    /// 是否支持 macOS / Whether macOS is supported
    let mac: Bool?
    /// 是否支持 Linux / Whether Linux is supported
    let linux: Bool?
}

// MARK: - 宽容数字解码 / Lenient number decoding

private extension KeyedDecodingContainer {

    /// 读一个可能以数字或字符串给出的整数，两种形态都收 /
    /// Reads an integer that may arrive as a number or as a string; both are accepted
    ///
    /// Steam 的同一字段在不同游戏上会换类型（实测 `required_age` 既有 `0` 也有 `"18"`），
    /// 所以不能直接 `decode(Int.self:)` /
    /// Steam switches the type of the same field between games, so a plain
    /// `decode(Int.self:)` would throw and lose the whole payload
    func flexibleInt(forKey key: Key) -> Int? {
        if let number = try? decodeIfPresent(Int.self, forKey: key) {
            return number
        }
        if let text = try? decodeIfPresent(String.self, forKey: key) {
            return Int(text)
        }
        return nil
    }
}

// MARK: - 派生属性 / Derived properties

extension SteamAppDetails {

    /// 是否免费，F2P 与 Demo 都算 / `true` for both free-to-play titles and demos
    var isFreeOrDemo: Bool { isFree == true || type == "demo" }

    /// 是否还没发售 / Whether the game has not been released yet
    var isComingSoon: Bool { releaseDate?.comingSoon == true }

    /// 支持平台的可读文本，如 `"Windows · macOS"` / Readable platform list such as "Windows · macOS"
    var platformText: String {
        guard let platforms else { return "—" }

        return [
            platforms.windows == true ? "Windows" : nil,
            platforms.mac == true ? "macOS" : nil,
            platforms.linux == true ? "Linux" : nil,
        ]
        .compactMap { $0 }
        .joined(separator: " · ")
    }
}

extension SteamMovie {

    /// 这个视频能不能播，只有 HLS 是 `AVPlayer` 直接可播的 /
    /// Whether this movie can be played; only the HLS URL works in `AVPlayer` directly
    var isPlayable: Bool { hlsH264 != nil }

    /// 优先用于播放的地址 / The URL to play
    var preferredURL: String? { hlsH264 }
}

extension Array where Element == SteamMovie {

    /// 主预告片：`highlight == true` 的那条，找不到就按 Steam 的返回顺序取第一条 /
    /// The highlight reel: the entry flagged `highlight`, falling back to Steam's own ordering
    var mainTrailer: SteamMovie? {
        first(where: { $0.highlight == true && $0.isPlayable })
            ?? first(where: { $0.isPlayable })
    }
}
