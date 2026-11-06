//
//  SaleEventList.swift
//  steaL
//
//  Created by yijiang on 2026/11/6.
//

import Foundation

// MARK: - 根对象 / Root

/// 自建后端 `GET /v1/sale-events` 的响应体：所有商店还没结束的活动。
/// The response of our backend's `GET /v1/sale-events`: every store's events that have not ended.
///
/// 字段与 `api/openapi.yaml` 的 `SaleEventList` 一一对应；时刻都是整秒 UTC，用 `.iso8601` 解码。
/// The fields match `SaleEventList` in `api/openapi.yaml`; times are whole-second UTC, decoded with `.iso8601`.
struct SaleEventList: Codable, Hashable, Sendable {

    /// 各商店最后一次成功刷新时间里最早的那个，表示最旧的那部分数据有多旧。
    /// The earliest of the stores' last successful refresh times: how old the stalest part of the data is.
    let refreshedAt: Date

    /// 进行中与将来的活动，按开始时间升序。
    /// Ongoing and upcoming events, sorted by start ascending.
    let events: [SaleEvent]
}

// MARK: - 活动 / Event

/// 一场商店大促或 Fest。
/// One store sale or fest.
struct SaleEvent: Codable, Hashable, Sendable, Identifiable {

    /// 在一份响应里唯一，可以当列表身份；跨刷新不保证不变。
    /// Unique within one response and usable as list identity; not guaranteed stable across refreshes.
    let id: String

    /// 哪个商店的活动。
    /// The store the event belongs to.
    let store: Store

    /// 活动类型。
    /// The event type.
    let kind: Kind

    /// 源页面上的原文名字（英文，不翻译）。
    /// The name exactly as on the source page (English, untranslated).
    let name: String

    /// 开始时刻。
    /// The start time.
    let start: Date

    /// 结束时刻（不含这一刻）。
    /// The end time (exclusive).
    let end: Date

    /// 这条数据提取自哪个网页。
    /// The web page this event was extracted from.
    let sourceURL: URL
}

// MARK: - 商店与类型 / Store and kind

extension SaleEvent {

    /// 活动所属的商店。
    /// The store an event belongs to.
    ///
    /// 后端以后会加新商店，不认识的值落到 `unknown`，不让整份响应解码失败。
    /// The backend will add stores later; unknown values land in `unknown` instead of failing the whole response.
    enum Store: RawRepresentable, Codable, Hashable, Sendable {
        case steam
        case epic
        case itchio
        case gog
        /// 这个版本还不认识的商店，带原始值。
        /// A store this version does not know yet, with its raw value.
        case unknown(String)

        /// 按接口里的字符串建值，不认识的落到 `unknown`，所以永远不会是 `nil`。
        /// Creates a value from the API string; unknown strings become `unknown`, so this is never `nil`.
        init?(rawValue: String) {
            switch rawValue {
            case "steam": self = .steam
            case "epic": self = .epic
            case "itchio": self = .itchio
            case "gog": self = .gog
            default: self = .unknown(rawValue)
            }
        }

        /// 接口里的字符串。
        /// The string used in the API.
        var rawValue: String {
            switch self {
            case .steam: "steam"
            case .epic: "epic"
            case .itchio: "itchio"
            case .gog: "gog"
            case .unknown(let rawValue): rawValue
            }
        }
    }

    /// 活动类型：Steam 分三种（对应 Steamworks 页面的三节），其他商店都是 `sale`。
    /// The event type: Steam has three (the three sections of the Steamworks page); other stores use `sale`.
    ///
    /// 后端以后会加新类型，不认识的值落到 `unknown`。
    /// The backend will add kinds later; unknown values land in `unknown`.
    enum Kind: RawRepresentable, Codable, Hashable, Sendable {
        /// Steam 季节大促（Winter Sale 等）。
        /// A Steam seasonal sale (Winter Sale and so on).
        case seasonalSale
        /// Steam 主题 Fest。
        /// A Steam themed fest.
        case themedFest
        /// Steam Next Fest（试玩节）。
        /// Steam Next Fest (the demo festival).
        case nextFest
        /// 其他商店的促销。
        /// A sale on another store.
        case sale
        /// 这个版本还不认识的类型，带原始值。
        /// A kind this version does not know yet, with its raw value.
        case unknown(String)

        /// 按接口里的字符串建值，不认识的落到 `unknown`，所以永远不会是 `nil`。
        /// Creates a value from the API string; unknown strings become `unknown`, so this is never `nil`.
        init?(rawValue: String) {
            switch rawValue {
            case "seasonalSale": self = .seasonalSale
            case "themedFest": self = .themedFest
            case "nextFest": self = .nextFest
            case "sale": self = .sale
            default: self = .unknown(rawValue)
            }
        }

        /// 接口里的字符串。
        /// The string used in the API.
        var rawValue: String {
            switch self {
            case .seasonalSale: "seasonalSale"
            case .themedFest: "themedFest"
            case .nextFest: "nextFest"
            case .sale: "sale"
            case .unknown(let rawValue): rawValue
            }
        }
    }
}
