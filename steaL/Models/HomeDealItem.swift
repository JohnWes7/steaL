//
//  HomeDealItem.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import Foundation

// MARK: - 主页条目 / Home feed item

/// 主页列表里的一条：ITAD 的 deal，加上从 Steam 那边补出来的 appID /
/// One row of the home feed: the ITAD deal plus the Steam appID resolved for it
struct HomeDealItem: Identifiable, Hashable, Sendable {

    /// ITAD 的原始条目，视图渲染用的都是它 /
    /// The untouched ITAD entry; everything the view renders comes from here
    let deal: ITADDeal

    /// Steam 关联状态，默认还在查 / Steam link state; `.pending` until it resolves
    var steam: SteamLink = .pending

    /// 用 ITAD UUID 做列表主键 / Uses the ITAD UUID as the list identity
    var id: String { deal.id }
}

// MARK: - Steam 关联状态 / Steam link state

extension HomeDealItem {

    /// 这个条目在 Steam 上的关联情况，三态互斥 /
    /// How this entry maps onto Steam; the three cases are mutually exclusive
    enum SteamLink: Hashable, Sendable {

        /// 还没查完 / Still resolving
        case pending

        /// 查到了 appID，可以进详情页 / Resolved: the detail page can open
        case resolved(Int)

        /// 查过了，但 ITAD 没有对应的 app 级条目 /
        /// Looked up, but ITAD has no app-level entry for this game
        case unavailable

        /// 可用的 appID，没有就是 `nil` / The appID when there is one
        var appID: Int? {
            if case .resolved(let id) = self { return id }
            return nil
        }

        /// 是否已经查完 / Whether resolution has finished
        var isSettled: Bool { self != .pending }
    }

    /// 这个条目可用的 appID / The appID for this entry, `nil` when there is none
    var appID: Int? { steam.appID }

    /// 是否已经查完 / Whether resolution has finished
    var isResolved: Bool { steam.isSettled }
}
