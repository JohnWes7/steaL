//
//  IOS26SearchView.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import FirebaseCore
import SwiftData
import SwiftUI

/// 搜索页。搜索完全交给系统：这里只挂一个 `.searchable`，聚焦、弹键盘、取消都由系统接管 /
/// Search screen. Search is delegated to the system: this view only attaches `.searchable`, while
/// focusing, raising the keyboard and cancelling are all handled for us.
struct IOS26SearchView: View {

    /// 搜索关键字 / Search keyword
    @State private var query = ""
    @State private var searchViewModel = SearchViewModel()

    /// 用户设置单例行 / Singleton user-settings row
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    /// 当前搜索区域 / Current search region
    private var region: AppRegion {
        settings.first?.region ?? .australia
    }

    /// 当前搜索语言 / Current search language
    private var language: AppLanguage {
        settings.first?.language ?? .english
    }

    /// 搜索任务参数；关键词或设置变化时重新搜索 / Search task inputs; reruns when the query or settings change
    private var searchRequest: SearchRequest {
        SearchRequest(query: trimmedQuery, region: region, language: language)
    }

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
        .task(id: searchRequest) {
            guard !trimmedQuery.isEmpty else {
                await searchViewModel.search(
                    query: trimmedQuery,
                    region: region,
                    language: language
                )
                return
            }

            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return }

            await searchViewModel.search(
                query: trimmedQuery,
                region: region,
                language: language
            )
        }
        .navigationDestination(for: GameDetailRoute.self) { route in
            GameDetailView(route: route)
        }
    }

    @ViewBuilder
    private var searchContent: some View {
        switch searchViewModel.state {
        case .idle:
            ContentUnavailableView {
                Label {
                    Text("Search the Steam store")
                } icon: {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(.brandGreen)
                }
            } description: {
                Text("Find games, expansions, and bundles.")
            }

        case .loading:
            VStack(spacing: 14) {
                ProgressView()
                    .tint(.brandBlue)

                Text("Searching the Steam store...")
                    .font(.system(.subheadline, weight: .medium))
                    .foregroundStyle(.secondaryText)
            }

        case .loaded(let items):
            if items.isEmpty {
                ContentUnavailableView {
                    Label {
                        Text("No Results for “\(trimmedQuery)”")
                    } icon: {
                        Image(systemName: "magnifyingglass")
                            .foregroundStyle(.brandGreen)
                    }
                } description: {
                    Text("Check the spelling or try a new search.")
                }
            } else {
                List(items) { item in
                    NavigationLink(value: GameDetailRoute(
                        appID: item.id,
                        uuid: nil,
                        title: item.name
                    )) {
                        searchResultRow(item)
                    }
                    .listRowInsets(EdgeInsets(top: 4, leading: 14, bottom: 4, trailing: 14))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .scrollIndicators(.hidden)
            }

        // 不给重试：改一下搜索词就会重新请求 / No retry: editing the query searches again
        case .failed(let error):
            LoadErrorView(error: error)
        }
    }

    private func searchResultRow(_ item: SteamSearchItem) -> some View {
        HStack(spacing: 0) {
            resultArtwork(item)

            VStack(alignment: .leading, spacing: 0) {
                Text(item.name)
                    .font(.system(.callout, weight: .semibold))
                    .foregroundStyle(.primaryText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 7)
        }
        // .background(Color.surfaceElevated.opacity(0.82))
        // .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
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
            Color.imagePlaceholder
            Image(systemName: symbol)
                .font(.system(.callout, weight: .semibold))
                .foregroundStyle(.secondaryText)
        }
    }
}

#Preview {
    // 预览不走 `AppDelegate`，直接连真实 Firebase；点进详情页要读 `AuthService` / `WishlistService` /
    // Previews skip `AppDelegate`, so connect to the real Firebase; the detail page reads `AuthService` / `WishlistService`
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()
    NavigationStack {
        IOS26SearchView()
    }
    .environment(AuthService())
    .environment(WishlistService())
}
