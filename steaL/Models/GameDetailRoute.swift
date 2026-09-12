//
//  GameDetailRoute.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import Foundation

// MARK: - 详情页路由 / Detail page route

/// 推入详情页时携带的参数，`.navigationDestination(item:)` 要求它可哈希 /
/// The payload handed to the detail page; `Hashable`, as `.navigationDestination(item:)` requires
struct GameDetailRoute: Hashable {

    /// Steam App ID，详情页的硬门槛 / Steam App ID; the detail page's hard requirement
    let appID: Int

    /// ITAD 游戏 UUID，主页路径一定有，搜索路径暂时没有 /
    /// ITAD game UUID; always present on the home path, absent on the search path for now
    let uuid: String?

    /// 列表上显示用的标题，只用于数据回来前占位，不是权威标题 /
    /// List title, a placeholder until real data arrives; never treated as authoritative
    let title: String
}
