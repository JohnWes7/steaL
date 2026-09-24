//
//  SteamAppDetailsData.swift
//  steaL
//
//  Created by yijiang on 2026/9/13.
//

import Foundation

// MARK: - 根对象 / Root

/// `appdetails` 的响应体；顶层是以 appid 字符串为 key 的字典，值里分 `success` 与 `data` /
/// Response of the Steam store `appdetails` endpoint; a dictionary keyed by appid, each value
/// carrying `success` and `data` — raw capture in `docs/Game Detail Planning.md` §9
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
/// 除标题与类型外字段全部可选，Steam 对不同游戏返回的差异很大 /
/// Everything except the title and type is optional; Steam's payload varies a lot between games
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
    /// 这个游戏的试玩版，仅本体条目有值 / Demos of this title, present on the base game's entry
    let demos: [SteamDemo]?

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
        case categories, genres, fullgame, demos
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

    /// `required_age` 有 `0` 与 `"18"` 两种形态，合成解码会让整页失败，所以两种都收 /
    /// `required_age` arrives both as `0` and as `"18"`, which the synthesised decoding rejects
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
        demos = try container.decodeIfPresent([SteamDemo].self, forKey: .demos)

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

/// 单个视频：`hls_h264` 是 `AVPlayer` 原生支持的播放列表，`webm` 只留给浏览器备用 /
/// One movie; `hls_h264` is an HLS playlist `AVPlayer` handles natively, `webm` is kept as a spare
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

/// 试玩版的引用（本体 `demos` 里的一项），appid 是数字 / Reference to a demo, one item of the base game's `demos`; the appid is a number
struct SteamDemo: Codable, Hashable, Sendable {

    /// 试玩版自己的 appid / The demo's own appid
    let appid: Int?
    /// 描述，实测为空字符串 / Description; observed empty in practice
    let description: String?
}

/// 名称型描述项，`categories` 与 `genres` 共用 / A named descriptor, shared by `categories` and `genres`
///
/// 两边 `id` 类型**不一致**（`genres` 给 `"1"`、`categories` 给 `2`），两种都收并统一成 `Int` /
/// The two payloads disagree on `id`: `"1"` versus `2`; both are accepted and normalised to `Int`
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

    /// 是否是试玩版条目 / Whether the entry is a demo
    var isDemo: Bool { type?.caseInsensitiveCompare("demo") == .orderedSame }

    /// 是否免费，F2P 与 Demo 都算 / `true` for both free-to-play titles and demos
    var isFreeOrDemo: Bool { isFree == true || isDemo }

    /// 是否还没发售 / Whether the game has not been released yet
    var isComingSoon: Bool { releaseDate?.comingSoon == true }

    /// 商店对这款游戏的定价定性 / How the store prices this title
    enum GameKind {
        /// 试玩版，不单独售卖 / A demo, never sold on its own
        case demo
        /// 限时免费（含免费保留），原价还在 / Free for a limited time; the regular price is still there
        case freeToKeep
        /// 永久免费（F2P），从来没有卖过 / Permanently free; it has never been sold
        case freeForever
        /// 还没发售；开了预购的有 `price_overview` / Not released yet; a pre-order has a `price_overview`
        case unreleased
        /// 正常售卖 / Sold normally
        case paid

        /// 要不要显示历史低价：永久免费的低价是它免费之前的价 /
        /// Whether the historical lows are worth showing; a permanently free title has none worth it
        var showsPriceHistory: Bool {
            switch self {
            case .freeForever: false
            case .demo, .freeToKeep, .unreleased, .paid: true
            }
        }
    }

    /// 游戏类型；三个条件并不互斥（试玩版与限时免费都是 `is_free == true`），顺序即优先级 /
    /// The kind of game; the flags are not mutually exclusive, so their order is the priority
    ///
    /// 两种免费用 `price_overview` 区分（永久免费是 `null`）/ The two free kinds differ by `price_overview`
    var gameKind: GameKind {
        if isDemo { return .demo }
        if isComingSoon { return .unreleased }
        if isFree == true { return priceOverview == nil ? .freeForever : .freeToKeep }
        return .paid
    }

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
