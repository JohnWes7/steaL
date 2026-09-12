//
//  IOS26SearchView.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import SwiftUI

/// iOS 26 原生搜索方案 / The native iOS 26 search approach.
///
/// 搜索能力完全交给系统：tab 那边声明 `role: .search`，这里只挂一个 `.searchable`。
/// 聚焦、弹键盘、取消、切走时收键盘，全部由系统接管 /
/// Search is delegated entirely to the system: the tab declares `role: .search` and this view only
/// attaches `.searchable`. Focus, raising the keyboard, cancel, and resigning on tab-away are all
/// handled for us.
///
/// 对照手写版的 `SearchView`：那边需要 `isSelected` 双向信号、`focusTask` 8 次补试循环、
/// 手写 Cancel 按钮、点空白收键盘、`isEditing` 判断；这里一个都不需要 /
/// Compare with the hand-rolled `SearchView`, which needed a two-way `isSelected` signal, an 8-step
/// focus retry loop, a hand-written cancel button, tap-to-dismiss and an `isEditing` flag.
/// None of that exists here.
struct IOS26SearchView: View {

    /// 搜索关键字，直接交给 `.searchable` / Search keyword, handed straight to `.searchable`
    @State private var query = ""

    var body: some View {
        ZStack {
            Color.appBackground
                .ignoresSafeArea()

            placeholderContent
        }
        .navigationTitle("Search")
        .navigationBarTitleDisplayMode(.inline)
        // 唯一的搜索相关代码：剩下的都归系统 /
        // The only search-related line: the rest belongs to the system
        .searchable(text: $query, prompt: "Search games, publishers...")
    }

    // MARK: - 占位内容 / Placeholder content

    /// 数据接入前先给出可读的占位：空关键字显示引导，有关键字则回显，
    /// 用来确认原生搜索框的输入确实传到了这一层 /
    /// Readable placeholder before data is wired up: a hint when the keyword is empty, an echo when
    /// it is not — which proves the native field's input actually reaches this layer
    @ViewBuilder
    private var placeholderContent: some View {
        VStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(.secondaryText)

            if trimmedQuery.isEmpty {
                Text("Search for a Steam game")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.primaryText)

                Text("Prices from every store will show up here.")
                    .font(.system(size: 14))
                    .foregroundStyle(.secondaryText)
            } else {
                Text("You typed")
                    .font(.system(size: 14))
                    .foregroundStyle(.secondaryText)

                Text(trimmedQuery)
                    .font(.system(size: 22, weight: .heavy, design: .rounded))
                    .foregroundStyle(.primaryText)
                    .multilineTextAlignment(.center)

                Text("Search is not wired up yet.")
                    .font(.system(size: 14))
                    .foregroundStyle(.secondaryText)
            }
        }
        .padding(.horizontal, 28)
        .frame(maxWidth: .infinity)
    }

    /// 去掉首尾空白的输入 / Keyword with surrounding whitespace removed
    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

#Preview {
    NavigationStack {
        IOS26SearchView()
    }
}
