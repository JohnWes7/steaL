//
//  HotDealsView.swift
//  steaL
//
//  Created by yijiang on 2026/9/28.
//

import SwiftUI

/// 全部 Hot Deals 的列表页，首页区块标题的「See All」推入。
/// The full Hot Deals list, pushed from the home section's See All.
///
/// 首页只放前几条，完整列表与之后的分页加载都放在这一页，首页下面的 Giveaways 不受影响。
/// The home page shows only the first few; the full list and later paging live here, leaving Giveaways on the home page alone.
struct HotDealsView: View {
    var body: some View {
        ZStack {
            // 背景色 / Background color
            Color.appBackground
                .ignoresSafeArea()
        }
        .navigationTitle("Hot Deals")
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    NavigationStack {
        HotDealsView()
    }
}
