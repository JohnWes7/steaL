//
//  HotDealsRequest.swift
//  steaL
//
//  Created by yijiang on 2026/10/9.
//

import Foundation

/// Hot Deals 列表的请求条件，换地区或换游戏大类都会让 `.task(id:)` 重新加载。
/// The conditions of a Hot Deals list request; a new region or genre makes `.task(id:)` reload.
struct HotDealsRequest: Equatable, Hashable, Sendable {
    /// 商店区域 / The store region.
    let region: AppRegion
    /// 选中的游戏大类，`nil` 表示全部 / The selected genre; `nil` means all genres.
    let genre: DealGenre?
}
