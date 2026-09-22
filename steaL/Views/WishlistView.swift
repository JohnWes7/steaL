//
//  WishlistView.swift
//  steaL
//
//  Created by yijiang on 2026/9/22.
//

import SwiftUI

struct WishlistView: View {

    var body: some View {
        // 列表还没接 Firestore，先放空状态占位 / Not wired to Firestore yet, so show the empty state for now
        ContentUnavailableView(
            "No Games Yet",
            systemImage: "heart",
            description: Text("Games you add to your wishlist will appear here.")
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.appBackground)
        // 顶栏与 Profile 同款：玻璃背景来自 `safeAreaBar`，内边距在 `PageHeaderView` 里 / Same top bar as Profile; blur from `safeAreaBar`, padding in the header
        .safeAreaBar(edge: .top, spacing: 0) {
            PageHeaderView(title: "Wishlist")
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}

#Preview {
    NavigationStack {
        WishlistView()
    }
}
