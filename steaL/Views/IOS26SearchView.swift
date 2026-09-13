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
    @State private var searchViewModel = SearchViewModel()

    /// 去掉首尾空白的输入 / Keyword with surrounding whitespace removed
    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        ZStack {
            Color.appBackground
                .ignoresSafeArea()

            searchContent
        }
        .toolbar(.hidden, for: .navigationBar)
        .searchable(text: $query, prompt: "Search games, publishers...")
        .task(id: trimmedQuery) {
            guard !trimmedQuery.isEmpty else {
                await searchViewModel.search(query: trimmedQuery)
                return
            }

            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return }

            await searchViewModel.search(query: trimmedQuery)
        }
    }

    @ViewBuilder
    private var searchContent: some View {
        switch searchViewModel.state {
        case .idle:
            Text("Search for a Steam game")
                .foregroundStyle(.secondaryText)

        case .loading:
            ProgressView("Searching...")
                .tint(.brandBlue)
                .foregroundStyle(.secondaryText)

        case .loaded(let items):
            if items.isEmpty {
                ContentUnavailableView.search(text: trimmedQuery)
            } else {
                List(items) { item in
                    Text(item.name)
                        .foregroundStyle(.primaryText)
                }
                .scrollContentBackground(.hidden)
            }

        case .failed(let error):
            ContentUnavailableView(
                error.title,
                systemImage: error.symbolName,
                description: Text(error.message)
            )
        }
    }
}

#Preview {
    NavigationStack {
        IOS26SearchView()
    }
}
