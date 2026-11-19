//
//  HomeDealItem.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import Foundation

// MARK: - 主页条目 / Home feed item

/// 主页列表里的一条：ITAD 的 deal，加上从 Steam 那边补出来的 appID。
/// One row of the home feed: the ITAD deal plus the Steam appID resolved for it.
struct HomeDealItem: Identifiable, Hashable, Sendable {

    /// ITAD 的原始条目，视图渲染用的都是它。
    /// The untouched ITAD entry; everything the view renders comes from here.
    let deal: ITADDeal

    /// Steam 关联状态，默认还在查。
    /// The Steam link state; `.pending` until it resolves.
    var steam: SteamLink = .pending

    /// ITAD 游戏 UUID。
    /// The ITAD game UUID.
    var itadUUID: String { deal.id }

    /// `Identifiable` 要求的列表主键，就是 ITAD UUID。
    /// The list identity `Identifiable` requires: the ITAD UUID.
    ///
    /// 协议规定必须叫 `id`，`ForEach(items)` 靠它区分每一行；代码里要用 UUID 时写 `itadUUID`，名字和 Android 一致。
    /// The protocol requires the name `id`, and `ForEach(items)` tells rows apart with it; code that needs the
    /// UUID uses `itadUUID`, matching the Android name.
    var id: String { itadUUID }
}

// MARK: - Steam 关联状态 / Steam link state

extension HomeDealItem {

    /// 这个条目在 Steam 上的关联情况，四态互斥。
    /// How this entry maps onto Steam; the four cases are mutually exclusive.
    enum SteamLink: Hashable, Sendable {

        /// 还没查完。
        /// Still resolving.
        case pending

        /// 查到了 appID，可以进详情页。
        /// Resolved: the detail page can open.
        case resolved(Int)

        /// 查过了，但 ITAD 没有对应的 app 级条目；重试也不会变。
        /// Looked up, but ITAD has no app-level entry for this game; a retry would not help.
        case notFound

        /// lookup 请求失败，下拉刷新会重查。
        /// The lookup request failed; pull to refresh looks it up again.
        case failed

        /// 可用的 appID，没有就是 `nil`。
        /// The appID when there is one, or `nil`.
        var appID: Int? {
            if case .resolved(let id) = self { return id }
            return nil
        }
    }

    /// 这个条目可用的 Steam appID，没有就是 `nil`。
    /// The Steam appID for this entry, or `nil` when there is none.
    var steamAppID: Int? { steam.appID }
}
