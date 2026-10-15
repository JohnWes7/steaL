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

    /// 要推入的详情页，`non-nil` 即跳转，返回时系统置回 `nil`。
    /// The detail page to push; a `non-nil` value navigates, and the system resets it to `nil` on back.
    @State private var detailRoute: GameDetailRoute?

    /// 进不了详情页时的提示文案，`non-nil` 即弹出。
    /// The warning shown when a game can't open its detail page; a `non-nil` value raises the alert.
    @State private var warning: String?

    /// 选中的游戏大类，`nil` 表示全部。
    /// The selected genre; `nil` means all genres.
    @State private var genre: DealGenre?

    private var region: AppRegion {
        settings.first?.region ?? .australia
    }

    /// 当前的请求条件，地区或大类变了 `.task(id:)` 就重新加载。
    /// The current request conditions; a new region or genre makes `.task(id:)` reload.
    private var request: HotDealsRequest {
        HotDealsRequest(region: region, genre: genre)
    }

    private var navTitle : String {
        var title = "Hot Deals" 
        if let genre {
            title += " - \(genre.title)"
        }
        return title
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
                        await viewModel.loadHotDeals(request: request)
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
        .navigationTitle(navTitle)
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                genreMenu
            }
        }
        // 换地区或大类时取消旧请求、重新加载 / A new region or genre cancels the old request and reloads
        .task(id: request) {
            await viewModel.loadIfNeeded(request: request)
        }
        // 未登录点心形时弹登录，同首页与详情页 / Sign-in for a heart tapped while signed out, as on the home and detail pages
        .sheet(isPresented: $isShowingSignIn) {
            SignInSheet()
        }
        .navigationDestination(item: $detailRoute) { route in
            GameDetailView(route: route)
        }
        // 进不了详情页时的提示，同首页 / Warning for a game that can't open its detail page, as on the home page
        .alert(
            "Can't Open This Game",
            isPresented: Binding(
                get: { warning != nil },
                set: { isPresented in
                    if !isPresented { warning = nil }
                }
            )
        ) {
            Button("OK", role: .cancel) { warning = nil }
        } message: {
            Text(warning ?? "")
        }
    }

    /// 导航栏上选游戏大类的菜单，选中某一类后图标变成实心。
    /// The navigation bar menu for choosing a genre; the icon turns filled once a genre is chosen.
    private var genreMenu: some View {
        let symbolName = genre == nil
            ? "line.3.horizontal.decrease.circle"
            : "line.3.horizontal.decrease.circle.fill"

        return Menu {
            Picker("Genre", selection: $genre) {
                Text("All Genres")
                    .tag(nil as DealGenre?)

                ForEach(DealGenre.allCases) { genre in
                    Text(genre.title)
                        .tag(Optional(genre))
                }
            }
        } label: {
            Label("Filter by Genre", systemImage: symbolName)
        }
    }

    /// 请求成功但列表为空时的提示：选了大类时给「显示全部」，否则给刷新入口。
    /// The note for a successful but empty list: Show All Genres when a genre is chosen, otherwise a refresh action.
    ///
    /// 没选大类时文案与样式同首页 `DealsSectionView` 的空状态。
    /// Without a genre, the copy and style match the home page's `DealsSectionView` empty state.
    @ViewBuilder
    private var emptyState: some View {
        if let genre {
            ContentUnavailableView {
                Label("No \(genre.title) Deals", systemImage: "tag.slash")
            } description: {
                Text("There are no discounted \(genre.title) games right now.")
            } actions: {
                Button("Show All Genres") {
                    self.genre = nil
                }
                .tint(.brandBlue)
            }
        } else {
            ContentUnavailableView {
                Label("No Deals Found", systemImage: "tag.slash")
            } description: {
                Text("There are no discounted games to show right now. Try again later.")
            } actions: {
                Button("Refresh") {
                    Task {
                        await viewModel.loadHotDeals(request: request)
                    }
                }
                .tint(.brandBlue)
            }
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
                        // 整张卡可点；`.plain` 让卡内心形的点击不被外层吞掉 /
                        // The whole card is tappable; `.plain` keeps the card's own heart clickable
                        Button {
                            handleTap(item)
                        } label: {
                            DealGridCardView(
                                deal: item.deal,
                                isFavorite: isFavorite(item),
                                isFavoriteEnabled: item.appID != nil,
                                onToggleFavorite: {
                                    toggleFavorite(item)
                                }
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint("Shows game details")
                        .onAppear {
                            loadNextPageIfNeeded(appearing: item, in: items)
                        }
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

    /// 列表底部的下一页入口，按 `nextPageState` 显示按钮、转圈或重试。
    /// The list footer for the next page, showing a button, a spinner, or a retry by `nextPageState`.
    ///
    /// 三种状态共用同一个最小高度，切换时下面的内容不会跳动。
    /// All three states share one minimum height so the layout does not jump between them.
    private var nextPageFooter: some View {
        Group {
            switch viewModel.nextPageState {
            case .idle:
                Button("Load More") {
                    loadNextPage()
                }

            case .loading:
                ProgressView()
                    .tint(.brandGreen)

            // 列表保留，只在按钮里提示失败并重试 / The list stays; only the button reports the failure and retries
            case .failed:
                Button("Couldn't Load. Try Again", systemImage: "arrow.clockwise") {
                    loadNextPage()
                }
            }
        }
        .tint(.brandBlue)
        .frame(maxWidth: .infinity, minHeight: 44)
        .padding(.vertical, 16)
    }

    /// 倒数第 6 张卡出现时自动加载下一页。
    /// Loads the next page automatically when the sixth-from-last card appears.
    ///
    /// - Parameters:
    ///   - item: 刚出现的卡片 / The card that just appeared.
    ///   - items: 当前的完整列表 / The current full list.
    private func loadNextPageIfNeeded(appearing item: HomeDealItem, in items: [HomeDealItem]) {
        // 加载中不重复请求；失败后不自动重试，等用户点底部的重试按钮 /
        // No duplicate request while loading; after a failure, wait for the footer's retry button instead of retrying automatically
        if viewModel.nextPageState != .idle { return }

        let triggerIndex = max(items.count - 6, 0)
        let triggerItem = items[triggerIndex]
        guard item.id == triggerItem.id else { return }

        loadNextPage()
    }

    /// 加载下一页，自动加载、「Load More」与失败后的重试共用。
    /// Loads the next page; shared by the automatic load, Load More, and the retry after a failure.
    ///
    /// 用不跟视图绑定的 `Task`：触发的卡片滑出屏幕时请求不会被取消。
    /// Uses a `Task` not tied to a view, so the request survives the trigger card leaving the screen.
    private func loadNextPage() {
        Task {
            await viewModel.loadNextPage(request: request)
        }
    }

    // MARK: - 收藏 / Favorite

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

        // 用户正看着这个折扣，记成已知低价，提醒就不会再推同一次打折
        // The user is looking at this sale, so it becomes the known low and the alert won't push the same sale again
        let dealInfo = item.deal.deal
        let knownLow = WishlistItem.KnownPrice(price: dealInfo.price, cut: dealInfo.cut, region: region)

        let wishlistItem = WishlistItem(
            appID: appID,
            name: item.deal.title,
            headerImageURL: item.deal.headerRatioArtworkURL?.absoluteString,
            itadID: item.deal.id,
            isComingSoon: nil,
            releaseCheckedAt: nil,
            knownLow: knownLow,
        )

        try? wishlistService.add(wishlistItem, uid: uid)

        // 第一次收藏时请求通知权限，这时用户看得懂提醒是干什么用的
        // Asks for notification permission on the first favourite, when the purpose of alerts is clear
        Task {
            await NotificationPermission.requestIfNeeded()
        }
    }

    // MARK: - 进详情页 / Opening the detail page

    /// 点卡片：按 Steam 关联的四种状态决定跳转还是提示。
    /// Handles a card tap: the four Steam link states decide between navigating and a warning.
    ///
    /// 同首页 `GamesView.handleTap`，只有查失败的提示不同：这一页没有下拉刷新。
    /// Mirrors the home page's `GamesView.handleTap`, except the failed-lookup message: this page has no pull to refresh.
    ///
    /// - Parameter item: 被点的优惠 / The tapped deal.
    private func handleTap(_ item: HomeDealItem) {
        switch item.steam {
        case .pending:
            // 补 appID 几百毫秒就回来，这期间静默忽略 / The appID lands within a few hundred milliseconds; ignore taps meanwhile
            break

        case .resolved(let appID):
            detailRoute = GameDetailRoute(appID: appID, uuid: item.id, title: item.deal.title)

        case .notFound:
            warning = "“\(item.deal.title)” is not available on Steam, so there is no detail page to show."

        // 查失败不代表 Steam 上没有 / A failed lookup does not mean it is missing
        case .failed:
            warning = "Couldn't check “\(item.deal.title)” on Steam. Please try again later."
        }
    }
}

#Preview {
    NavigationStack {
        HotDealsView()
    }
}
