//
//  SectionStatusViews.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 区块内加载中：一块局部转圈，不把同页其它区块也打回加载态 /
/// In-section loading: a local spinner that leaves the other sections on the page alone
struct SectionLoadingView: View {

    var body: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 40)
    }
}

/// 空列表的刷新按钮 / The refresh button of an empty list
struct SectionRetryButton: View {

    /// 按钮文案，如 `Refresh` / The button copy, such as `Refresh`
    let title: String

    /// 点击动作 / The tap action
    let action: () -> Void

    var body: some View {
        Button(title, action: action)
            .tint(.brandBlue)
    }
}

#Preview("加载中 / Loading") {
    SectionLoadingView()
        .background(Color.appBackground)
}

#Preview("空列表按钮 / Empty-list button") {
    SectionRetryButton(title: "Refresh", action: {})
        .padding(20)
        .background(Color.appBackground)
}
