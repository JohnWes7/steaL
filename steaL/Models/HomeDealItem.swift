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
///
/// **为什么不直接在 `ITADDeal` 上加一个 `appID` 字段 / Why not just add `appID` to `ITADDeal`:**
///
/// `ITADDeal` 是 `/deals/v2` 的**解码产物**，它的每个字段都对应响应里的一个 JSON 键。
/// 而 `appID` 是**另一个接口**（`lookup/shop/61/id/v1`）异步补齐的，来源不同、时机不同。
/// 混进 DTO 会让它同时承担「响应映射」和「本地状态」两件事，而且 `Codable` 会忽略这个字段，
/// `Hashable` 的语义也变得含糊 /
/// `ITADDeal` is the decoded shape of `/deals/v2`, so every field maps to a JSON key. `appID`
/// instead comes from a second endpoint and is filled in later. Putting it on the DTO would make
/// that type carry both response mapping and local state, and `Codable` would silently ignore it
struct HomeDealItem: Identifiable, Hashable, Sendable {

    /// ITAD 的原始条目，视图渲染用的都是它 /
    /// The untouched ITAD entry; everything the view renders comes from here
    let deal: ITADDeal

    /// 补出来的 Steam appID / The resolved Steam appID
    ///
    /// 三种状态都由 `steam` 表达，不要只看 `nil` /
    /// All three states live in `steam`; never read this `nil` on its own
    var steam: SteamLink = .pending

    /// 用 ITAD UUID 做列表主键 / Uses the ITAD UUID as the list identity
    var id: String { deal.id }
}

// MARK: - Steam 关联状态 / Steam link state

extension HomeDealItem {

    /// 这个条目在 Steam 上的关联情况 /
    /// How this entry maps onto Steam
    ///
    /// 三态是必要的：`appID == nil` 既可能是「还在查」也可能是「查过了没有」，
    /// 而这两者对用户的意义完全不同 —— 前者应该等一下，后者才该弹 warning /
    /// Three states are required: `appID == nil` alone cannot tell "still resolving" from
    /// "resolved and absent", and those two mean very different things to the user
    enum SteamLink: Hashable, Sendable {

        /// 还没查完（首屏刚出、预取还在路上）/ Still resolving; the feed just appeared
        case pending

        /// 查到了 appID，可以进详情页 / Resolved: the detail page can open
        case resolved(Int)

        /// 查过了，但这个游戏在 ITAD 的 Steam 映射表里没有 app 级条目 /
        /// Looked up, but ITAD has no app-level entry for this game
        ///
        /// 实测这种情况约占放宽筛选后的 11%（`sub/` 有、`app/` 没有），
        /// 当前首页配置下是 0% / Measured at roughly 11% with loose filters, 0% with the
        /// current home configuration
        case unavailable

        /// 可用的 appID，没有就是 `nil` / The appID when there is one
        var appID: Int? {
            if case .resolved(let id) = self { return id }
            return nil
        }

        /// 是否已经查完（不论成功与否）/ Whether resolution has finished, successfully or not
        var isSettled: Bool { self != .pending }
    }

    /// 这个条目现在能不能点开详情页 / Whether this row can open the detail page right now
    var appID: Int? { steam.appID }

    /// 是否已经查完 / Whether resolution has finished
    var isResolved: Bool { steam.isSettled }
}
