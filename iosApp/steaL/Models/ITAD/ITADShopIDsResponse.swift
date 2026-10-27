//
//  ITADShopIDsResponse.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import Foundation

// MARK: - 根对象 / Root

/// `POST .../lookup/shop/61/id/v1` 的响应体：请求体是 UUID 数组，响应是**以 UUID 为 key 的字典** /
/// Response of the shop-id lookup endpoint: an array of UUIDs in, a **UUID-keyed dictionary** out
/// — raw capture in `docs/ITAD API Handoff.md` §21
struct ITADShopIDsResponse: Decodable, Hashable, Sendable {

    /// UUID → 该游戏在 Steam 上的商店 ID 列表 /
    /// UUID to the list of that game's shop IDs on Steam
    ///
    /// **值为可选**：没收录的 UUID 返回 `null`（实测）/ **Optional**: an untracked UUID comes back as `null`
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

    /// 某个 ITAD 游戏对应的全部 Steam appID 候选，保持响应顺序；UUID 不在响应里、值是 `null` 或只有 `sub/` 都返回 `[]` /
    /// Every Steam appID candidate for one ITAD game, in response order; `[]` when the UUID is absent,
    /// the value is `null`, or only `sub/` entries exist
    ///
    /// 可能不止一个，而且第一个不一定能用：Monster Hunter: World 给了 `app/601100`、`app/743980`、`app/582010`，
    /// 只有最后一个在 Steam 上还存在（2026-09-23 实测）/ There can be several, and the first may be dead
    func appIDs(for uuid: String) -> [Int] {
        // `app/` 的位置**不固定**（2026-09-16 抓包里是 index 2），必须按前缀查 / `app/` is not always first; match by prefix
        shopIDs(for: uuid).compactMap { shopID in
            guard shopID.hasPrefix(ITADShopID.appPrefix) else { return nil }
            return Int(shopID.dropFirst(ITADShopID.appPrefix.count))
        }
    }

    /// 某个 ITAD 游戏在 Steam 上的全部商店 ID，`app/` 与 `sub/` 都在 /
    /// Every Steam shop ID for one ITAD game, both `app/` and `sub/` entries
    func shopIDs(for uuid: String) -> [String] {
        // `entries[uuid]` 是 `[String]??`，两层都要摊平 / `entries[uuid]` is `[String]??`; flatten both
        entries[uuid].flatMap({ $0 }) ?? []
    }

    /// 本次响应里有几个 UUID 是服务端收录了的 / How many requested UUIDs the service actually tracks
    var trackedCount: Int {
        entries.values.count { ($0?.isEmpty == false) }
    }
}

// MARK: - 商店 ID 前缀 / Shop ID prefixes

/// `lookup/shop/{shopId}/id/v1` 响应里的前缀常量（请求体传的是裸 UUID，没有前缀）/
/// Prefix constants used by the shop-id lookup response; the request body carries bare UUIDs
enum ITADShopID {

    /// Steam 应用 ID 前缀，例如 `"app/1245620"` / Steam application ID prefix
    static let appPrefix = "app/"

    /// Steam 订阅 / 包 ID 前缀，例如 `"sub/432247"`；一个游戏通常有多个，**不能**当 appID 用 /
    /// Steam subscription (package) ID prefix; a game usually has several, never usable as an appID
    static let subPrefix = "sub/"
}
