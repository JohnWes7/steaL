//
//  HotDealsView.swift
//  steaL
//
//  Created by yijiang on 2026/9/28.
//

import SwiftData
import SwiftUI

/// 全部 Hot Deals 的列表页，首页区块标题的「See All」推入。
/// The full Hot Deals list, pushed from the home section's See All.
///
/// 首页只放前几条，完整列表与之后的分页加载都放在这一页，首页下面的 Giveaways 不受影响。
/// The home page shows only the first few; the full list and later paging live here, leaving Giveaways on the home page alone.
struct HotDealsView: View {
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    @Environment(AuthService.self) private var authService
    @Environment(WishlistService.self) private var wishlistService

    /// viewmodel
    @State private var viewModel: HotDealsViewModel = HotDealsViewModel()

    /// signin sheet
    @State private var isShowingSignIn: Bool = false

    private var region: AppRegion {
        settings.first?.region ?? .australia
    }

    var body: some View {
        ZStack {
            // 背景色 / Background color
            Color.appBackground
                .ignoresSafeArea()

            switch viewModel.dealState {
            case .loading:
                ProgressView()
                    .tint(.brandGreen)

            case .failed(let error):
                LoadErrorView(error: error) {
                    Task {
                        await viewModel.loadHotDeals(region: region)
                    }
                }

            case .loaded(let items):
                if items.isEmpty {
                    emptyState
                } else {
                    dealList(items)
                }
            }
        }
        .navigationTitle("Hot Deals")
        .navigationBarTitleDisplayMode(.large)
        // 换地区时取消旧请求、重新加载 / A region change cancels the old request and reloads
        .task(id: region) {
            await viewModel.loadIfNeeded(region: region)
        }
    }

    /// 请求成功但列表为空时的提示，附刷新入口。
    /// The note for a successful but empty list, with a refresh action.
    ///
    /// 文案与样式同首页 `DealsSectionView` 的空状态。
    /// The copy and style match the home page's `DealsSectionView` empty state.
    private var emptyState: some View {
        ContentUnavailableView {
            Label("No Deals Found", systemImage: "tag.slash")
        } description: {
            Text("There are no discounted games to show right now. Try again later.")
        } actions: {
            Button("Refresh") {
                Task {
                    await viewModel.loadHotDeals(region: region)
                }
            }
            .tint(.brandBlue)
        }
    }

    /// 已加载的优惠列表。
    /// The loaded list of deals.
    ///
    /// - Parameter items: 要显示的优惠 / The deals to show.
    /// - Returns: 列表视图 / The list view.
    private func dealList(_ items: [HomeDealItem]) -> some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 160, maximum: 200), spacing: 12)],
                    spacing: 12
                ) {
                    ForEach(items) { item in
                        DealGridCardView(
                            deal: item.deal,
                            isFavorite: isFavorite(item),
                            isFavoriteEnabled: item.appID != nil,
                            onToggleFavorite: {
                                toggleFavorite(item)
                            }
                        )
                    }
                }

                if viewModel.nextOffset != nil {
                    nextPageFooter
                }
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 16)
            .padding(.top, 20)
        }
    }

    @ViewBuilder
    private var nextPageFooter: some View {
        Button("load More") {
            Task {
                await viewModel.loadNextPage(region: region)
            }
        }
        .tint(.brandBlue)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
    }

    /// MARK: - Favorite

    private func isFavorite(_ item: HomeDealItem) -> Bool {
        guard let appID = item.appID else {
            return false
        }
        return wishlistService.contains(appID: appID)
    }

    private func toggleFavorite(_ item: HomeDealItem) {
        guard let appID = item.appID else {
            return
        }

        guard let uid = authService.uid else {
            isShowingSignIn = true
            return
        }

        if wishlistService.contains(appID: appID) {
            wishlistService.remove(appID: appID, uid: uid)
            return
        }

        let wishlistItem = WishlistItem(
            appID: appID,
            name: item.deal.title,
            headerImageURL: item.deal.headerRatioArtworkURL?.absoluteString,
            itadID: item.deal.id,
            isComingSoon: nil,
            releaseCheckedAt: nil,
        )

        try? wishlistService.add(wishlistItem, uid: uid)
    }
}

#Preview {
    NavigationStack {
        HotDealsView()
    }
}
