//
//  AIModelSettingsView.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import SwiftUI

/// 从 Profile 的 AI Model 行推入：选用哪个模型、管理自己的服务商；内容还没做 /
/// Pushed from Profile's AI Model row: choose the model and manage your own providers; the content is not built yet
struct AIModelSettingsView: View {

    var body: some View {
        List {
        }
        // 和 Profile 一样露出 appBackground / Shows appBackground, like Profile
        .scrollContentBackground(.hidden)
        .background(Color.appBackground)
        .navigationTitle("AI Model")
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    // 推入的页面，放进 `NavigationStack` 才有导航栏 / A pushed page; it needs a `NavigationStack` for its navigation bar
    NavigationStack {
        AIModelSettingsView()
    }
}
