//
//  ProfileHeaderView.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import SwiftUI

/// Profile 顶部栏：小字品牌名 + 大字页面标题，与主页头部同款排版 /
/// Profile top bar: small wordmark above a large page title, matching the home header layout
struct ProfileHeaderView: View {

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("STEAL")
                .font(.system(size: 13, weight: .bold, design: .default))
                .foregroundStyle(.brandGreen)

            Text("Profile")
                .font(.system(size: 44, weight: .bold, design: .default))
                .foregroundStyle(.primaryText)
        }
    }
}

#Preview {
    ProfileHeaderView()
        .padding(.horizontal, 14)
        .padding(.top, 2)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.bar)
}
