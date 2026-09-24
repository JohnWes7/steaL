//
//  WishlistItem.swift
//  steaL
//
//  Created by yijiang on 2026/9/23.
//

import Foundation
import FirebaseFirestore

/// `users/{uid}/wishlist/{appID}` 的一个文档，只存画列表行要的快照字段 /
/// One `users/{uid}/wishlist/{appID}` document, holding only the snapshot fields a list row needs
struct WishlistItem: Codable, Identifiable {

    /// Steam App ID，冗余一份，解码时不用解析文档 ID / Steam App ID, duplicated so decoding needn't parse the document ID
    let appID: Int

    /// 列表行标题 / The list row title
    let name: String

    /// 列表行封面，免得逐个请求 `appdetails` / The row's cover, so rows don't each fetch `appdetails`
    let headerImageURL: String?

    /// ITAD 游戏 UUID，之后查价格省一次 `lookup/v1` / ITAD game UUID; saves a `lookup/v1` when fetching prices later
    let itadID: String?

    /// 是否未发售（Steam `release_date.coming_soon`）；nil = 收藏时不知道，`WishlistViewModel` 核对后回写 /
    /// Whether the game is unreleased (Steam `release_date.coming_soon`); nil = unknown when saved, checked and written back via `WishlistViewModel`
    let isComingSoon: Bool?

    // 服务器还没确认的本地快照里是 nil / nil in the local snapshot until the server confirms the write
    // `ServerTimestamp` 没有无参 init，去掉 `= nil` 成员初始化器就必须传它 / `ServerTimestamp` has no plain init, so without `= nil` the memberwise init requires it
    /// 加入时间，写入时由服务器填 / When it was added; filled in by the server on write
    @ServerTimestamp var addedAt: Date? = nil // swiftlint:disable:this implicit_optional_initialization

    var id: Int { appID }
}
