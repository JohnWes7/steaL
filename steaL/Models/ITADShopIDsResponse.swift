//
//  ITADShopIDsResponse.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import Foundation

// MARK: - 根对象 / Root

/// `POST https://api.isthereanydeal.com/lookup/shop/61/id/v1` 的响应体 /
/// Response of the shop-id lookup endpoint
///
/// 请求体是一组 ITAD 游戏 UUID（`["<uuid>", ...]`），响应是**以 UUID 为 key 的字典**，
/// 而不是数组 —— 所以这里包一层，让「取 Steam appid」这件事只写一次 /
/// The request body is an array of ITAD UUIDs, but the response is a dictionary keyed by UUID
/// rather than an array, so this wrapper keeps "pull the Steam appid out" written in one place
///
/// 真实响应示例 / A real response:
///
/// ```json
/// {
///   "018d937f-590c-728b-ac35-38bcff85f086": ["sub/432247", "sub/440410", "app/1245620", "sub/440408"],
///   "00000000-0000-0000-0000-000000000000": null
/// }
/// ```
struct ITADShopIDsResponse: Decodable, Hashable, Sendable {

    /// UUID → 该游戏在 Steam 上的商店 ID 列表 /
    /// UUID to the list of that game's shop IDs on Steam
    ///
    /// **值为可选**：请求了但服务端没收录的 UUID 会返回 `null`（实测确认），
    /// 这样能区分「查过但没有」和「压根没查过」/
    /// **The value is optional**: a UUID that was requested but is not tracked comes back as
    /// `null` (verified), which keeps "looked up and found nothing" distinct from "never asked"
    let entries: [String: [String]?]

    /// 直接按字典解码根对象 / Decodes the root dictionary as-is
    init(from decoder: any Decoder) throws {
        entries = try [String: [String]?](from: decoder)
    }

    /// 供预览与测试直接构造 / Lets previews and tests build one directly
    init(entries: [String: [String]?]) {
        self.entries = entries
    }
}

// MARK: - 取 Steam appid / Pulling the Steam appid

extension ITADShopIDsResponse {

    /// 某个 ITAD 游戏对应的 Steam appID / The Steam appID for one ITAD game
    ///
    /// 返回 `nil` 的三种情况都视为「这个游戏进不了详情页」/
    /// All three `nil` cases mean the same thing: this game cannot open the detail page
    ///
    /// 1. 这个 UUID 不在响应里（没查过） / the UUID is absent from the response
    /// 2. 值本身是 `null`（ITAD 没收录） / the value itself is `null`
    /// 3. 只有 `sub/` 没有 `app/`（老游戏或冷门游戏，ITAD 未建应用级索引）/
    ///    only `sub/` entries exist, which happens for older or obscure games
    ///
    /// - Parameter uuid: ITAD 游戏 UUID / the ITAD game UUID
    /// - Returns: Steam appID，取不到时为 `nil` / the Steam appID, or `nil` when unavailable
    func appID(for uuid: String) -> Int? {
        // `entries[uuid]` 是 `[String]??`（字典下标本身可选，值也可选），
        // `flatMap` 把两层摊平，写 `?? nil` 消不掉 /
        // `entries[uuid]` is `[String]??` (optional subscript over an optional value), so
        // `flatMap` is what flattens both layers
        guard let shopIDs = entries[uuid].flatMap({ $0 }) else { return nil }

        // 注意 `app/` 在数组里的位置**不固定**，实测既有 index 0 也有 index 1：
        //   ["app/1202900", "sub/415283"]
        //   ["sub/256235", "app/831050", "sub/691307"]
        // 所以必须按前缀查找，不能取 `.first` /
        // The `app/` entry is not always first (index 0 and 1 are both observed), so it has to be
        // found by prefix rather than by position
        guard let appEntry = shopIDs.first(where: { $0.hasPrefix(ITADShopID.appPrefix) }) else {
            return nil
        }

        return Int(appEntry.dropFirst(ITADShopID.appPrefix.count))
    }

    /// 某个 ITAD 游戏在 Steam 上的全部商店 ID，`app/` 与 `sub/` 都在 /
    /// Every Steam shop ID for one ITAD game, both `app/` and `sub/` entries
    ///
    /// 详情页目前用不到，但排查映射问题时很有用 /
    /// Not needed by the detail page yet, but handy when debugging a mapping problem
    func shopIDs(for uuid: String) -> [String] {
        entries[uuid].flatMap({ $0 }) ?? []
    }

    /// 本次响应里有几个 UUID 是服务端收录了的 / How many requested UUIDs the service actually tracks
    var trackedCount: Int {
        entries.values.count { ($0?.isEmpty == false) }
    }
}

// MARK: - 商店 ID 前缀 / Shop ID prefixes

/// `lookup/shop/{shopId}/id/v1` 返回值里的前缀常量 /
/// Prefix constants used by the shop-id lookup response
///
/// 这些前缀是**响应里**的形态，和请求体无关 —— 请求体传的是裸 UUID /
/// These prefixes appear in the response only; the request body carries bare UUIDs
enum ITADShopID {

    /// Steam 应用 ID 前缀，例如 `"app/1245620"` / Steam application ID prefix
    static let appPrefix = "app/"

    /// Steam 订阅 / 包 ID 前缀，例如 `"sub/432247"` /
    /// Steam subscription (package) ID prefix
    ///
    /// 一个游戏通常有多个 `sub/`，它们**不能**当作 appID 使用 /
    /// A game usually has several `sub/` entries and they must never be used as an appID
    static let subPrefix = "sub/"
}

// MARK: - 预览 / Preview

#if DEBUG

extension ITADShopIDsResponse {

    /// 预览用示例，覆盖三种情况：有 `app/`、只有 `sub/`、值为 `null` /
    /// Sample covering all three shapes: has `app/`, `sub/` only, and `null`
    static let previewSample = ITADShopIDsResponse(entries: [
        "018d937f-590c-728b-ac35-38bcff85f086": ["sub/432247", "sub/440410", "app/1245620", "sub/440408"],
        "018d937f-3a1d-720b-aafa-2c1f21404d3f": ["sub/517969"],
        "00000000-0000-0000-0000-000000000000": nil,
    ])
}

#endif
