//
//  WishlistItem.swift
//  steaL
//
//  Created by yijiang on 2026/9/23.
//

import Foundation
import FirebaseFirestore

/// `users/{uid}/wishlist/{appID}` 的一个文档，只存画列表行和做提醒要的快照字段。
/// One `users/{uid}/wishlist/{appID}` document, holding only the snapshot fields a list row and the alerts need.
struct WishlistItem: Codable, Identifiable {

    /// Steam App ID，冗余一份，解码时不用解析文档 ID。
    /// Steam App ID, duplicated so decoding needn't parse the document ID.
    let appID: Int

    /// 列表行标题。
    /// The list row title.
    let name: String

    /// 列表行封面，免得逐个请求 `appdetails`。
    /// The row's cover, so rows don't each fetch `appdetails`.
    let headerImageURL: String?

    /// ITAD 游戏 UUID，之后查价格省一次 `lookup/v1`。
    /// ITAD game UUID; saves a `lookup/v1` when fetching prices.
    ///
    /// 收藏时没有的，由 Wishlist 页 lookup 后回写。
    /// The wishlist page looks up and writes back a missing one.
    let itadID: String?

    /// 是否未发售（Steam `release_date.coming_soon`）；nil 表示收藏时不知道。
    /// Whether the game is unreleased (Steam `release_date.coming_soon`); nil means unknown when saved.
    ///
    /// `WishlistViewModel` 向 Steam 核对后回写。
    /// `WishlistViewModel` checks it with Steam and writes it back.
    let isComingSoon: Bool?

    /// 上次向 Steam 核对发售状态的时间；nil 表示没核对过。
    /// When the release status was last checked with Steam; nil means never.
    ///
    /// Wishlist 页一天内不重复核对。
    /// The wishlist page rechecks at most once a day.
    let releaseCheckedAt: Date?

    /// 用户已经知道的最低价：收藏时看到的折扣价，或上次降价通知的价格；nil 表示不知道，或回到原价后被清空。
    /// The lowest price the user already knows about: the sale price seen when saving, or the last price-drop notification;
    /// nil means none, or cleared after returning to full price.
    ///
    /// 降价提醒只在价格低于它时发，用户刚看过的折扣不会再推一次。价格和地区放在一个结构体里，
    /// Firestore 里是同一个 map 字段，要么都有、要么都没有。
    /// A price-drop alert fires only below it, so a sale the user just saw isn't pushed again. The price and its region share one struct,
    /// stored as a single Firestore map field, so they are either both present or both absent.
    let knownLow: KnownPrice?

    /// 加入时间，写入时由服务器填；服务器还没确认的本地快照里是 nil。
    /// When it was added, filled in by the server on write; nil in the local snapshot until the server confirms.
    ///
    /// `ServerTimestamp` 没有无参 init，去掉 `= nil` 成员初始化器就必须传它。
    /// `ServerTimestamp` has no plain init, so without `= nil` the memberwise init requires it.
    @ServerTimestamp var addedAt: Date? = nil // swiftlint:disable:this implicit_optional_initialization

    /// 列表身份，就是 Steam App ID。
    /// The list identity, which is the Steam App ID.
    var id: Int { appID }
}

extension WishlistItem {

    /// 用户已经知道的一个价格，连同它所在的地区。
    /// A price the user already knows about, together with its region.
    ///
    /// 不同地区的货币不同，换了地区，旧价格就不能拿来比。
    /// Regions use different currencies, so an old price can't be compared once the region changes.
    struct KnownPrice: Codable, Equatable {

        /// 以最小货币单位表示的价格，同 `ITADPrice.amountInt`。
        /// The price in the smallest currency unit, as in `ITADPrice.amountInt`.
        let amount: Int

        /// 查到这个价格时的地区。
        /// The region the price was fetched for.
        let region: AppRegion
    }
}

extension WishlistItem.KnownPrice {

    /// 收藏时用：在打折就记下现价，没打折返回 nil。
    /// For saving: records the current price when on sale, or returns nil when not.
    ///
    /// 写在扩展里，保留成员初始化器。
    /// Declared in an extension so the memberwise initializer stays.
    ///
    /// - Parameters:
    ///   - price: 现价 / The current price.
    ///   - cut: 折扣百分比，0 表示没打折 / The discount percentage; 0 means not on sale.
    ///   - region: 价格所在的地区 / The region the price belongs to.
    init?(price: ITADPrice, cut: Int, region: AppRegion) {
        guard cut > 0 else {
            return nil
        }
        self.amount = price.amountInt
        self.region = region
    }
}
