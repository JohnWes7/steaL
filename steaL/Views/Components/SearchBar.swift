//
//  SearchBar.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import SwiftUI

/// 搜索栏的共享外壳：`GamesView` 的入口按钮和 `SearchView` 的输入框都套这一层，
/// 所以两处的尺寸、配色、圆角完全一致 /
/// Shared chrome for the search bar. Both the `GamesView` entry button and the `SearchView`
/// text field wrap this shell, so size, colour and corner radius match exactly.
///
/// 这一点是整条链路里消除突兀感的关键：切 tab 时页面内容是瞬切的，
/// 只有搜索栏在两屏之间"位置和长相都没变"，视觉上才会觉得是同一个控件留在原地 /
/// This is what removes the jarring feel: the tab switch itself is an instant swap, so the
/// transition only reads as smooth when the bar keeps the same look and position on both screens.
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

/// `GamesView` 顶部的搜索入口：长得和真实搜索框一样，但本身不可编辑，
/// 点击只发出"去搜索"的信号 /
/// Search affordance at the top of `GamesView`. It looks like a real search field but is not
/// editable — tapping it only sends a "go search" signal.
///
/// 这里刻意不用 `TextField`：如果它是可编辑的，用户可能已经在主页输入了内容，
/// 然后被切到另一个页面，输入丢失、两处状态还会不同步 /
/// Deliberately not a `TextField`: an editable field here would let the user start typing on the
/// home screen and then get moved to another screen, losing the input and desyncing two states.
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
