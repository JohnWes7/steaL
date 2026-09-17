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
    // let onSearchTapped: () -> Void

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
                        DealsSectionView(
                            state: gamesViewModel.dealState,
                            isFavorite: gamesViewModel.isFavorite,
                            onToggleFavorite: gamesViewModel.toggleFavorite,
                            onSelect: handleTap,
                            retry: retryDeals
                        )
                    } header: {
                        hotDealsHeader
                    }

                    Section {
                        GiveawaysSectionView(state: gamesViewModel.giveawaysState, retry: retryGiveaways)
                    } header: {
                        giveawayHeader
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 16)
                .padding(.top, 20)
            }
        }
        // `safeAreaBar` 提供玻璃背景与边缘模糊，但不替内容加内边距，所以这里自己写 / `safeAreaBar` supplies the blur, not the padding
        .safeAreaBar(edge: .top, spacing: 0) {
            GamesHeaderView()
                .padding(.horizontal, 14)
                .padding(.top, 2)
                .padding(.bottom, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                // .background(.bar)
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

    /// 条目的点击行为：三态对应三种结果，`Int?` 区分不了「还在查」与「查过没有」/
    /// What happens when a row is tapped; three states, which a single `Int?` could not express
    private func handleTap(_ item: HomeDealItem) {
        switch item.steam {
        case .pending:
            // 预取几百毫秒就回来，这期间静默忽略 / Prefetch lands within a few hundred milliseconds
            break

        case .resolved(let appID):
            // 赋值即跳转，`item:` 非空就推栈 / Assigning navigates: `.navigationDestination(item:)` pushes once non-nil
            homeDetailItem = GameDetailRoute(appID: appID, uuid: item.id, title: item.deal.title)

        case .unavailable:
            warning = "“\(item.deal.title)” is not available on Steam, so there is no detail page to show."
        }
    }

    // MARK: - 重试 / Retry

    /// 重试 deals，只影响第一个区块 / Retries deals; only the first section is affected
    private func retryDeals() {
        Task { await gamesViewModel.retryDeals() }
    }

    /// 重试 giveaways，只影响第二个区块 / Retries giveaways; only the second section is affected
    private func retryGiveaways() {
        Task { await gamesViewModel.retryGiveaways() }
    }

    // MARK: - 区块标题 / Section titles

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
}

#Preview {
    // 复刻 `MainTabView`：`navigationDestination` 只在 `NavigationStack` 里生效 / Mirrors `MainTabView`; the modifier needs a `NavigationStack`
    NavigationStack {
        GamesView()
    }
    .modelContainer(for: UserSettingModel.self, inMemory: true)
}
