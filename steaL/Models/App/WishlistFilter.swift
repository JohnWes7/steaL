//
//  WishlistFilter.swift
//  steaL
//
//  Created by yijiang on 2026/9/24.
//

import Foundation

/// Wishlist 顶部分段控件的筛选项；打折 / 史低由每行的价格算出，未发售读 Firestore 的 `isComingSoon` 并向 Steam 核对 /
/// The wishlist's segmented filter; on-sale and all-time-low come from each row's price, upcoming from Firestore's `isComingSoon` checked against Steam
enum WishlistFilter: String, CaseIterable, Identifiable {
    case all
    case onSale
    case allTimeLow
    case upcoming

    var id: String { rawValue }

    /// 分段上的短标签；iPhone 上四段放不下「All-Time Low」/ Short segment label; "All-Time Low" won't fit four segments on iPhone
    var title: String {
        switch self {
        case .all: return "All"
        case .onSale: return "On Sale"
        case .allTimeLow: return "Lowest"
        case .upcoming: return "Upcoming"
        }
    }

    /// 完整名称，给 VoiceOver 和菜单样式用 / Full name, for VoiceOver and the menu style
    var fullName: String {
        switch self {
        case .all: return "All"
        case .onSale: return "On Sale"
        case .allTimeLow: return "All-Time Low"
        case .upcoming: return "Upcoming"
        }
    }
}
