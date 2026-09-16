//
//  ProfileView.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import SwiftUI

struct ProfileView: View {
    
    private var profileViewModel = ProfileViewModel()
    
    var body: some View {
        ZStack {
            // 背景色 / Background color
            Color.appBackground
                .ignoresSafeArea()
        }
        // 顶栏与主页同款：玻璃背景 + 内边距写在调用点 / Same top bar as home; the blur comes from here, padding at the call site
        .safeAreaBar(edge: .top, spacing: 0) {
            ProfileHeaderView()
                .padding(.horizontal, 14)
                .padding(.top, 2)
                .padding(.bottom, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(.bar)
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}

#Preview {
    // 复刻 `MainTabView`：`toolbar` 只在 `NavigationStack` 里生效 / Mirrors `MainTabView`; the modifier needs a `NavigationStack`
    NavigationStack {
        ProfileView()
    }
}
