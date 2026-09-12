//
//  IOS26SearchView.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import SwiftUI

/// 搜索页。搜索完全交给系统：这里只挂一个 `.searchable`，聚焦、弹键盘、取消都由系统接管 /
/// Search screen. Search is delegated to the system: this view only attaches `.searchable`, while
/// focusing, raising the keyboard and cancelling are all handled for us.
struct IOS26SearchView: View {

    /// 搜索关键字 / Search keyword
    @State private var query = ""

    var body: some View {
        ZStack {
            Color.appBackground
                .ignoresSafeArea()

            placeholderContent
        }
        .toolbar(.hidden, for: .navigationBar)
        .searchable(text: $query, prompt: "Search games, publishers...")
    }

    // MARK: - 占位内容 / Placeholder content

    /// 数据接入前的占位：空关键字显示引导，有关键字则回显 /
    /// Placeholder before data is wired up: a hint when empty, an echo of the keyword otherwise
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
