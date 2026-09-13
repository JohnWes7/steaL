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
            searchMessage(
                symbol: "magnifyingglass",
                title: "Search the Steam store",
                message: "Find games, expansions, and bundles."
            )

        case .loading:
            VStack(spacing: 14) {
                ProgressView()
                    .tint(.brandBlue)

                Text("Searching the Steam store...")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(.secondaryText)
            }

        case .loaded(let items):
            if items.isEmpty {
                ContentUnavailableView.search(text: trimmedQuery)
            } else {
                List(items) { item in
                    searchResultRow(item)
                        .listRowInsets(EdgeInsets(top: 4, leading: 14, bottom: 4, trailing: 14))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .scrollIndicators(.hidden)
            }

        case .failed(let error):
            ContentUnavailableView(
                error.title,
                systemImage: error.symbolName,
                description: Text(error.message)
            )
        }
    }

    private func searchResultRow(_ item: SteamSearchItem) -> some View {
        HStack(spacing: 0) {
            resultArtwork(item)

            VStack(alignment: .leading, spacing: 0) {
                Text(item.name)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.primaryText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 7)
        }
        .background(Color.surfaceElevated.opacity(0.82))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func resultArtwork(_ item: SteamSearchItem) -> some View {
        AsyncImage(url: URL(string: item.tinyImage ?? "")) { phase in
            switch phase {
            case .success(let image):
                image
                    .resizable()
                    .scaledToFit()
            case .failure:
                artworkPlaceholder(symbol: "photo.badge.exclamationmark")
            default:
                artworkPlaceholder(symbol: "photo")
            }
        }
        .frame(width: 145, height: 55)
    }

    private func artworkPlaceholder(symbol: String) -> some View {
        ZStack {
            Color.saleSurface
            Image(systemName: symbol)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.secondaryText)
        }
    }

    private func searchMessage(symbol: String, title: String, message: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: symbol)
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(.brandGreen)

            Text(title)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.primaryText)

            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(.secondaryText)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 28)
    }
}

#Preview {
    NavigationStack {
        IOS26SearchView()
    }
}
