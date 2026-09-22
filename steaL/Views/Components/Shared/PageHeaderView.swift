//
//  PageHeaderView.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import SwiftUI

/// Tab 顶部栏：小字品牌名 + 大字页面标题，与主页头部同款排版；内边距自带，页面只管放进 `safeAreaBar` /
/// Tab top bar: small wordmark above a large page title, matching the home header; owns its padding, pages only place it in `safeAreaBar`
struct PageHeaderView: View {

    /// 大字页面标题 / Large page title
    let title: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("STEAL")
                .font(.system(size: 13, weight: .bold, design: .default))
                .foregroundStyle(.brandGreen)

            Text(title)
                .font(.system(size: 44, weight: .bold, design: .default))
                .foregroundStyle(.primaryText)
        }
        .padding(.horizontal, 14)
        .padding(.top, 2)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    PageHeaderView(title: "Profile")
        .background(.bar)
}
