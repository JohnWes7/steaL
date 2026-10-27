//
//  WishlistPriceRequest.swift
//  steaL
//
//  Created by yijiang on 2026/9/24.
//

import Foundation

/// Wishlist 查价的触发条件：收藏增删或区域变化都会让 `.task(id:)` 重跑 /
/// What triggers a wishlist price load: adding, removing or a region change reruns `.task(id:)`
struct WishlistPriceRequest: Equatable, Hashable, Sendable {
    let appIDs: [Int]
    let region: AppRegion
}
