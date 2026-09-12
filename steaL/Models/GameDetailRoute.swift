//
//  GameDetailRoute.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import Foundation

// MARK: - 详情页路由 / Detail page route

/// 推入详情页时携带的参数 / The payload handed to the detail page
///
/// 必须是 `Hashable` —— `.navigationDestination(item:)` 要求绑定的值可哈希 /
/// Must be `Hashable`, which `.navigationDestination(item:)` requires of the bound value
///
/// **两个 ID 一起传，而不是只传其中一个 / Why both IDs travel together:**
///
/// 详情页需要 `appid` 去取 Steam 数据（页面本体），需要 `uuid` 去取 ITAD 的价格与评分。
/// 从主页进入时两个都是现成的 —— `uuid` 来自 `/deals/v2`，`appid` 来自列表加载时的那次批量
/// lookup。把它们一起带上，详情页内部**一次 lookup 都不用发**，三个请求可以全部并行 /
/// The detail page needs `appid` for Steam (the page itself) and `uuid` for ITAD prices and
/// reviews. Both are already known on the home path, so passing them together means the detail
/// page issues zero lookups and can start every request at once
///
/// 从搜索进入时会缺 `uuid`（`storesearch` 只给 appid），那时 `uuid` 为 `nil`，
/// 由详情页自己补一次 lookup / The search path has no `uuid` yet, so it passes `nil` there
struct GameDetailRoute: Hashable {

    /// Steam App ID，详情页的硬门槛 / Steam App ID; the detail page's hard requirement
    let appID: Int

    /// ITAD 游戏 UUID，主页路径一定有，搜索路径暂时没有 /
    /// ITAD game UUID; always present on the home path, absent on the search path for now
    let uuid: String?

    /// 列表上显示用的标题。只为了让详情页在数据回来前有个标题可显示，
    /// **不能**当成权威标题 —— Steam 返回的（可能已本地化的）名字才是 /
    /// Title used on the list. Only lets the detail page show something before its data arrives;
    /// never treat it as authoritative, since Steam's (possibly localised) name wins
    let title: String
}
