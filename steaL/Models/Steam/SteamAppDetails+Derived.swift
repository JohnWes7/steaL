//
//  SteamAppDetails+Derived.swift
//  steaL
//
//  Created by yijiang on 2026/9/24.
//

import Foundation

// MARK: - 派生属性 / Derived properties

extension SteamAppDetails {

    /// 是否是试玩版条目 / Whether the entry is a demo
    var isDemo: Bool { type?.caseInsensitiveCompare("demo") == .orderedSame }

    /// 是否还没发售 / Whether the game has not been released yet
    var isComingSoon: Bool { releaseDate?.comingSoon == true }

    /// 商店对这款游戏的定价定性 / How the store prices this title
    enum GameKind {
        /// 试玩版，不单独售卖 / A demo, never sold on its own
        case demo
        /// 永久免费（F2P），从来没有卖过 / Permanently free; it has never been sold
        case freeForever
        /// 还没发售；开了预购的有 `price_overview` / Not released yet; a pre-order has a `price_overview`
        case unreleased
        /// 正常售卖，含限时免费（100% 折扣，原价还在）/ Sold normally, including a 100%-off giveaway
        case paid

        /// 要不要显示历史低价：永久免费的低价是它免费之前的价 /
        /// Whether the historical lows are worth showing; a permanently free title has none worth it
        var showsPriceHistory: Bool {
            switch self {
            case .freeForever: false
            case .demo, .unreleased, .paid: true
            }
        }

        /// 能不能收藏：试玩版应该收藏本体，永久免费没有价格可追踪 /
        /// Whether it can be wishlisted; a demo's full game is the one to track, and a free-forever title has no price
        var canWishlist: Bool {
            switch self {
            case .demo, .freeForever: false
            case .unreleased, .paid: true
            }
        }

        /// 要不要给 AI 购买建议：试玩版不卖，永久免费不用花钱，都没有「买不买」可问 /
        /// Whether AI purchase advice applies; a demo is not sold and a free-forever title costs nothing
        var canGetAIAdvice: Bool {
            switch self {
            case .demo, .freeForever: false
            case .unreleased, .paid: true
            }
        }
    }

    /// 游戏类型；三个条件并不互斥（试玩版与限时免费都是 `is_free == true`），顺序即优先级 /
    /// The kind of game; the flags are not mutually exclusive, so their order is the priority
    var gameKind: GameKind {
        if isDemo { return .demo }
        if isComingSoon { return .unreleased }

        // 永久免费没有 `price_overview`；限时免费有原价，按付费处理 / A giveaway keeps its price, so it counts as paid
        let isFreeForever = isFree == true && priceOverview == nil
        if isFreeForever { return .freeForever }

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
