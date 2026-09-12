//
//  GamesView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import SwiftData
import SwiftUI

struct GamesView: View {
    /// 点击顶部搜索入口时交给 `MainTabView` 处理：切到 Search tab 并聚焦输入框 /
    /// Handed to `MainTabView` when the top search affordance is tapped: switch to the Search tab and focus the field
    let onSearchTapped: () -> Void

    /// 用户设置单例行，只读区域等偏好 / Singleton user-settings row; read for the region and other preferences
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]
    /// 列表数据源，负责请求与状态 / Data source for the list; owns fetching and state
    @State private var gamesViewModel = GamesViewModel()
    /// 当前加载任务；区域变化时取消上一个 / The in-flight load task; cancelled when the region changes
    @State private var loadTask: Task<Void, Never>?
    /// 是否已触发过首帧加载 / Whether the initial load has been started
    @State private var hasStarted = false

    /// 要推入的详情页。`non-nil` 即跳转，系统返回时会被置回 `nil` /
    /// The detail page to push. A `non-nil` value navigates, and the system sets it back to
    /// `nil` when the user goes back
    @State private var homeDetailItem: GameDetailRoute?

    /// 进不了详情页时的提示文案，`non-nil` 即弹出 / Warning text; a `non-nil` value raises the alert
    @State private var warning: String?

    /// 当前区域，设置就绪前回退澳洲 / Current region; falls back to Australia before settings load
    private var region: AppRegion {
        settings.first?.region ?? .australia
    }

    /// 首帧加载一次，之后只由区域变化触发重载 / Loads once, then only a region change triggers a reload
    private func reload() {
        loadTask?.cancel()
        loadTask = Task { await gamesViewModel.load(region: region) }
    }

    /// 两列等宽网格 / Two equal-width grid columns
    private let gridColumns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
    ]
    
    /// 页面骨架：背景 + 两个各自管理状态的区块 + 顶部固定 header /
    /// Screen skeleton: background, two self-managed sections and the pinned header
    var body: some View {
        ZStack {
            // 背景色 / Background color
            Color.appBackground
                .ignoresSafeArea()

            ScrollView {
                LazyVStack(spacing: 12) {
                    Section {
                        dealsSection
                    } header: {
                        hotDealsHeader
                    }

                    Section {
                        giveawaySection
                    } header: {
                        giveawayHeader
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 16)
                .padding(.top, 20)
            }
        }
        // 玻璃背景与滚动边缘模糊交给 iOS 26 的 `safeAreaBar`。它不会替内容加 padding，
        // 所以内边距得自己写 /
        // The iOS 26 `safeAreaBar` supplies the glass background and scroll edge blur. It does
        // not add padding on its own, so the padding is written out here
        .safeAreaBar(edge: .top, spacing: 0) {
            headerContent
                .padding(.horizontal, 14)
                .padding(.top, 2)
                .padding(.bottom, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(.bar)
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            // 只在首次出现时加载：切 Tab 回来不重复请求 / Loads only on first appearance; tab switches do not refetch
            guard !hasStarted else { return }
            hasStarted = true
            reload()
        }
        .onChange(of: region) {
            // 设置里的区域变化才重新请求 / Refetches only when the region changes in settings
            reload()
        }
        // 进不了详情页时的提示 / Warning shown when a game cannot open its detail page
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
        .navigationDestination(item: $homeDetailItem, destination: { route in
            GameDetailView(route: route)
        })
    }

    // MARK: - 进详情页 / Opening the detail page

    /// 条目的点击行为 / What happens when a row is tapped
    ///
    /// 三种状态对应三种结果，这正是 `HomeDealItem.steam` 用三态而不是 `Int?` 的原因 ——
    /// 只看 `appID == nil` 无法区分「还在查」和「查过了没有」/
    /// Three states, three outcomes: this is exactly why `steam` is a three-case enum rather than
    /// an `Int?`, since a bare `nil` cannot tell "still resolving" from "resolved and absent"
    private func handleTap(_ item: HomeDealItem) {
        switch item.steam {
        case .pending:
            // 预取通常几百毫秒就回来，这期间静默忽略，不打扰用户 /
            // Prefetch usually lands within a few hundred milliseconds; stay quiet until then
            break

        case .resolved(let appID):
            // 赋值即跳转：`.navigationDestination(item:)` 在 `non-nil` 时推入详情页 /
            // Assigning navigates: `.navigationDestination(item:)` pushes once the value is non-nil
            homeDetailItem = GameDetailRoute(appID: appID, uuid: item.id, title: item.deal.title)

        case .unavailable:
            warning = "“\(item.deal.title)” is not available on Steam, so there is no detail page to show."
        }
    }

    // MARK: - 状态分支 / State branches

    /// 区块内加载中 / In-section loading
    @ViewBuilder
    private var loadingState: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 40)
    }

    /// 区块内加载失败，附重试入口 / In-section failure with a retry action
    @ViewBuilder
    private func errorState(_ error: LoadError, retry: @escaping () async -> Void) -> some View {
        ContentUnavailableView {
            Label(error.title, systemImage: error.symbolName)
                .foregroundStyle(.secondaryText)
        } description: {
            Text(error.message)
                .foregroundStyle(.secondaryText)
        } actions: {
            retryButton(title: "Try Again", action: retry)
        }
        .frame(maxWidth: .infinity)
    }

    /// 区块内列表为空，附刷新入口 / In-section empty list with a refresh action
    @ViewBuilder
    private var emptyState: some View {
        ContentUnavailableView {
            Label("No Deals Found", systemImage: "tag.slash")
                .foregroundStyle(.secondaryText)
        } description: {
            Text("There are no discounted games to show right now. Try again later.")
                .foregroundStyle(.secondaryText)
        } actions: {
            retryButton(title: "Refresh") {
                await gamesViewModel.retryDeals()
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// 失败态与空态共用的重试按钮 / Retry button shared by the failure and empty states
    @ViewBuilder
    private func retryButton(title: String, action: @escaping () async -> Void) -> some View {
        Button(title) {
            Task { await action() }
        }
        .buttonStyle(.borderedProminent)
        .tint(.brandBlue)
    }

    // MARK: - 内容 / Content

    /// 「Hot Deals」区块内容，跟随 dealState 渲染加载 / 失败 / 空 / 列表 /
    /// Hot Deals section content; renders loading, failure, empty or the list based on dealState
    @ViewBuilder
    private var dealsSection: some View {
        switch gamesViewModel.dealState {
        case .loading:
            loadingState
        case .failed(let error):
            errorState(error) {
                await gamesViewModel.retryDeals()
            }
        case .loaded(let items):
            // 请求成功但列表为空 / Request succeeded with an empty list
            if items.isEmpty {
                emptyState
            } else {
                dealsContent(items)
            }
        }
    }

    /// 首条 hero 卡 + 其余两列网格 / Hero card first, then a two-column grid
    ///
    /// 收 `[HomeDealItem]` 而不是 `ITADDealsResponse`：条目除了 deal 还带着 Steam appID 的解析结果，
    /// 视图只需要渲染，不需要知道分页字段 /
    /// Takes `[HomeDealItem]` rather than `ITADDealsResponse`: each row now also carries its Steam
    /// appID resolution, and the view never needs the pagination fields
    @ViewBuilder
    private func dealsContent(_ items: [HomeDealItem]) -> some View {

        // 首条 hero 卡 / Hero card for the first item
        if let firstItem = items.first {
            // 卡片直接包成 `Button`：点击结果由 `handleTap` 决定，`.plain` 让卡片内收藏按钮的点击不被外层吞掉 /
            // The card is wrapped in a plain `Button`: `handleTap` decides the outcome, and `.plain` keeps the card's own favourite button clickable
            Button {
                handleTap(firstItem)
            } label: {
                DealCardView(
                    deal: firstItem.deal,
                    isFavorite: gamesViewModel.isFavorite(firstItem.id),
                    onToggleFavorite: {
                        gamesViewModel.toggleFavorite(firstItem.id)
                    }
                )
            }
            .buttonStyle(.plain)
        }

        // 其余条目排成两列网格 / Rest of the items in a two-column grid
        LazyVGrid(columns: gridColumns, spacing: 12) {
            ForEach(items.dropFirst()) { item in
                Button {
                    handleTap(item)
                } label: {
                    DealGridCardView(
                        deal: item.deal,
                        isFavorite: gamesViewModel.isFavorite(item.id),
                        onToggleFavorite: {
                            gamesViewModel.toggleFavorite(item.id)
                        }
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    /// 「Giveaways」区块内容，跟随 giveawaysState 渲染加载 / 失败 / 空 / 行列表 /
    /// Giveaways section content; renders loading, failure, empty or the rows based on giveawaysState
    @ViewBuilder
    private var giveawaySection: some View {
        switch gamesViewModel.giveawaysState {
        case .loading:
            loadingState
        case .failed(let error):
            errorState(error) {
                await gamesViewModel.retryGiveaways()
            }
        case .loaded(let giveaways):
            if giveaways.isEmpty {
                giveawayEmptyState
            } else {
                VStack(spacing: 10) {
                    ForEach(giveaways) { giveaway in
                        GiveawayRowView(giveaway: giveaway)
                    }
                }
            }
        }
    }

    /// Giveaways 为空时的轻量提示 / Lightweight note when no giveaways are active
    @ViewBuilder
    private var giveawayEmptyState: some View {
        Text("No giveaways right now. Check back later.")
            .font(.system(size: 14))
            .foregroundStyle(.secondaryText)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 「Hot Deals」区块标题 / Section title for the Hot Deals block
    @ViewBuilder
    private var hotDealsHeader: some View {
        HStack(spacing: 10) {
            Image(systemName: "flame.fill")
                .font(.system(size: 24))
                .foregroundStyle(.orange)

            Text("Hot Deals")
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(.primaryText)

            Spacer(minLength: 0)
        }
        .padding(.bottom, 2)
    }

    /// 「Giveaways」区块标题 / Section title for the Giveaways block
    @ViewBuilder
    private var giveawayHeader: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                Image(systemName: "gift.fill")
                    .font(.system(size: 24))
                    .foregroundStyle(.brandGreen)

                Text("Giveaways")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(.primaryText)

                Spacer(minLength: 0)
            }
        }
        // 区块间留白，与上一区块拉开距离 / Gap above the section, separating it from the previous block
        .padding(.top, 20)
        .padding(.bottom, 2)
    }


    
    /// 顶部栏内容：品牌标题 + 搜索框。只有内容，不含背景与内边距 ——
    /// 内边距和背景都写在 `body` 里的 `.safeAreaBar` / `.safeAreaInset` 调用点上 /
    /// Top bar content: brand title plus search field. Content only, no background or padding —
    /// both live at the `.safeAreaBar` / `.safeAreaInset` call site in `body`.
    @ViewBuilder
    private var headerContent: some View {
        VStack(alignment: .leading, spacing: 8) {
            header
            searchField
        }
    }

    /// 品牌标题区：左侧标语与 steaL 字样，右侧通知入口 / Brand header: tagline and steaL wordmark on the left, notification entry on the right
    @ViewBuilder
    private var header: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 0) {
                Text("STEAM PRICE TRACKER")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.brandGreen)
                
                HStack(spacing: 0) {
                    Text("stea")
                        .foregroundStyle(.primaryText)
                    
                    Text("L")
                        .foregroundStyle(.brandBlue)
                }
                .font(.system(size: 44, weight: .heavy, design: .rounded))
            }
            
            Spacer()
            
            notificationButton
        }
    }
    
    /// 右上角通知按钮，附带未读小红点 / Notification button in the top-right, with an unread badge
    @ViewBuilder
    private var notificationButton: some View {
        Button {
            // 打开通知页面
        } label: {
            Image(systemName: "bell.fill")
                .font(.system(size: 21, weight: .semibold))
                .foregroundStyle(.primaryText)
                .frame(width: 56, height: 56)
                .background(Color.surfaceElevated)
                .clipShape(Circle())
                .overlay(alignment: .topTrailing) {
                    Circle()
                        .fill(Color.brandBlue)
                        .frame(width: 11, height: 11)
                        .offset(x: -8, y: 8)
                }
        }
        .accessibilityLabel("Notifications")
    }
    
    /// 搜索入口。外观与 `SearchView` 的输入框完全一致（共用 `SearchBarShell`），
    /// 但本身不可编辑：点击只发出"去搜索"的信号，由 `MainTabView` 把它变成切 tab /
    /// Search entry point. Identical in appearance to the `SearchView` text field (both wrap
    /// `SearchBarShell`), but not editable: tapping only sends a "go search" signal, which
    /// `MainTabView` turns into a tab switch
    @ViewBuilder
    private var searchField: some View {
        SearchBarButton(placeholder: "Search games, publishers...", action: onSearchTapped)
    }
}

#Preview {
    // 复刻 `MainTabView` 的接线：`GamesView` 自己用 `.navigationDestination(item:)` 推栈，
    // 但那个修饰符只有在 `NavigationStack` 的内容树里才生效 /
    // Mirrors `MainTabView`: `GamesView` pushes through its own
    // `.navigationDestination(item:)`, which only takes effect inside a `NavigationStack`
    NavigationStack {
        GamesView(onSearchTapped: {})
    }
    .modelContainer(for: UserSettingModel.self, inMemory: true)
}
