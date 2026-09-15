//
//  SearchBar.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import SwiftUI

/// 搜索栏的共享外壳：入口按钮与真实输入框都套这一层，两处尺寸、配色、圆角完全一致 /
/// Shared chrome for the search bar, so both call sites match exactly
///
/// 切 tab 时只有它位置与长相不变，视觉上才像同一个控件留在原地 / What keeps the tab switch smooth
struct SearchBarShell<Content: View>: View {

    /// 壳里放按钮文案或真实输入框 / Either the button label or the real text field
    @ViewBuilder var content: Content

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 19, weight: .medium))
                .foregroundStyle(.secondaryText)

            content
        }
        .padding(.horizontal, 20)
        .frame(height: 45)
        .background(Color.appBackground)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

/// `GamesView` 顶部的搜索入口：长得像输入框但不可编辑，点击只发出「去搜索」的信号 /
/// Search affordance at the top of `GamesView`; tapping it only sends a "go search" signal
///
/// 刻意不用 `TextField`：可编辑会在切 tab 时丢输入 / Deliberately not a `TextField`, which would lose input
struct SearchBarButton: View {

    /// 占位文案 / Placeholder copy
    let placeholder: String

    /// 点击回调，由 `MainTabView` 注入 / Tap callback, injected by `MainTabView`
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            SearchBarShell {
                Text(placeholder)
                    .font(.system(size: 17))
                    .foregroundStyle(.secondaryText)

                Spacer(minLength: 0)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(placeholder)
    }
}

#Preview {
    VStack(spacing: 20) {
        SearchBarButton(placeholder: "Search games, publishers...") {}

        SearchBarShell {
            TextField("Search games, publishers...", text: .constant(""))
                .font(.system(size: 17))
                .foregroundStyle(.primaryText)
        }
    }
    .padding(14)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.surfaceElevated)
}
