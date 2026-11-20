//
//  GamesView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import FirebaseCore
import SwiftData
import SwiftUI

/// 首页：Hot Deals 前 5 条与 Giveaways 两个区块，顶部是品牌栏。
/// The home page: the first five Hot Deals and the Giveaways, under the brand header.
struct GamesView: View {
    /// 用户设置单例行，只读区域等偏好。
    /// The singleton user-settings row; read for the region and other preferences.
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    /// 登录状态，收藏要用 uid。
    /// Sign-in state; favouriting needs the uid.
    @Environment(AuthService.self) private var authService

    /// 全 App 共用的 wishlist。
    /// The wishlist shared by the whole app.
    @Environment(WishlistService.self) private var wishlistService

    /// 两个区块的数据状态。
    /// The data state of both sections.
    @State private var gamesViewModel = GamesViewModel()

    /// deals 区块正在跑的任务；同区块的新请求先取消它。
    /// The deals section's running task; a new request for the section cancels it first.
    ///
    /// 每个区块各存一个，旧请求一定被新请求接手，晚到的旧结果不会盖掉新结果（换地区、下拉刷新、重试互相交错时）。
    /// 是 `nil` 就表示页面还没加载过。
    /// One per section, so a newer request always takes over and a late stale result never overwrites a fresh
    /// one (when region changes, refreshes and retries overlap). `nil` means the page has not loaded yet.
    @State private var dealsTask: Task<Void, Never>?

    /// giveaways 区块正在跑的任务；同区块的新请求先取消它。
    /// The giveaways section's running task; a new request for the section cancels it first.
    @State private var giveawaysTask: Task<Void, Never>?

    /// 要推入的详情页，非 `nil` 即跳转，系统返回时置回 `nil`。
    /// The detail page to push; a non-`nil` value navigates, and the system resets it to `nil` on back.
    @State private var homeDetailItem: GameDetailRoute?

    /// 要推入的全部 Hot Deals 页，区块标题置为非 `nil`，返回时系统置回 `nil`。
    /// The full Hot Deals page to push; the section title sets it, and the system resets it to `nil` on back.
    ///
    /// 用 `item:` 而不是 `isPresented:`：后者推入带 `@Query` 的页面会让导航陷入死循环。
    /// Uses `item:` rather than `isPresented:`, which loops when the pushed page holds a `@Query`.
    @State private var hotDealsRoute: HotDealsRoute?

    /// 进不了详情页时的提示文案，非 `nil` 即弹出。
    /// The warning text when a game cannot open its detail page; a non-`nil` value raises the alert.
    @State private var warning: String?

    /// 未登录点心形时弹登录 sheet。
    /// Shows the sign-in sheet when a signed-out user taps a heart.
    @State private var isShowingSignIn = false

    /// 当前区域，设置就绪前回退澳洲。
    /// The current region; falls back to Australia before settings load.
    private var region: AppRegion {
        settings.first?.region ?? .australia
    }

    /// 页面骨架：背景、两个各自管理状态的区块、顶部固定的品牌栏。
    /// The screen skeleton: background, two self-managed sections, and the pinned header.
    var body: some View {
        ZStack {
            // 背景色。
            // Background colour.
            Color.appBackground
                .ignoresSafeArea()

            ScrollView {
                LazyVStack(spacing: 12) {
                    Section {
                        DealsSectionView(
                            state: gamesViewModel.dealState,
                            isFavorite: isFavorite,
                            onToggleFavorite: toggleFavorite,
                            onSelect: handleTap,
                            retry: { loadDeals(showsLoading: true) }
                        )
                    } header: {
                        hotDealsHeader
                    }

                    Section {
                        GiveawaysSectionView(
                            state: gamesViewModel.giveawaysState,
                            retry: { loadGiveaways(showsLoading: true) }
                        )
                    } header: {
                        giveawayHeader
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 16)
                .padding(.top, 20)
            }
            // 系统下拉刷新，也是 appID 查询失败后的重试入口。
            // System pull to refresh; also how a failed appID lookup gets retried.
            .refreshable {
                // 两块都换成新请求，旧内容留到新数据回来。
                // Both sections switch to new requests, keeping the old content until new data arrives.
                let deals = loadDeals(showsLoading: false)
                let giveaways = loadGiveaways(showsLoading: false)
                // 等的是独立 Task：刷新中途写状态会重绘并取消 refreshable 自己的 Task，独立 Task 不受影响，转圈等到两块都结束。
                // Waits on standalone tasks: mid-refresh state writes redraw and cancel refreshable's own task, which
                // standalone tasks survive, so the spinner stays until both sections finish.
                await deals.value
                await giveaways.value
            }
        }
        // `safeAreaBar` 提供玻璃背景与边缘模糊；内边距在 `GamesHeaderView` 里。
        // `safeAreaBar` supplies the glass background and edge blur; padding lives in `GamesHeaderView`.
        .safeAreaBar(edge: .top, spacing: 0) {
            GamesHeaderView()
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            // 只在第一次出现时加载：切 tab 回来、从详情页返回都不重复请求。
            // Loads only on first appearance; switching tabs or coming back from the detail page does not refetch.
            guard dealsTask == nil else { return }
            loadDeals(showsLoading: true)
            loadGiveaways(showsLoading: true)
        }
        .onChange(of: region) {
            // 只重新加载 deals：giveaways 的请求不带地区，换地区拿到的还是同一份。
            // Only the deals reload: the giveaways request carries no region, so it would return the same list.
            loadDeals(showsLoading: true)
        }
        // 进不了详情页时的提示。
        // The warning shown when a game cannot open its detail page.
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
        .navigationDestination(item: $homeDetailItem) { route in
            GameDetailView(route: route)
        }
        .navigationDestination(item: $hotDealsRoute) { _ in
            HotDealsView()
        }
        .sheet(isPresented: $isShowingSignIn) {
            SignInSheet()
        }
    }

    // MARK: - 加载 / Loading

    /// 取消 deals 区块的旧任务，启动新的一次，返回它。
    /// Cancels the deals section's old task, starts a new one, and returns it.
    ///
    /// - Parameter showsLoading: 是否先切到加载态；下拉刷新传 `false` / Whether to switch to loading first; pull to refresh passes `false`.
    /// - Returns: 新任务；下拉刷新要等它结束 / The new task, which pull to refresh waits for.
    @discardableResult
    private func loadDeals(showsLoading: Bool) -> Task<Void, Never> {
        dealsTask?.cancel()
        let currentRegion = region
        let task = Task {
            await gamesViewModel.updateDeals(region: currentRegion, showsLoading: showsLoading)
        }
        dealsTask = task
        return task
    }

    /// 取消 giveaways 区块的旧任务，启动新的一次，返回它。
    /// Cancels the giveaways section's old task, starts a new one, and returns it.
    ///
    /// - Parameter showsLoading: 是否先切到加载态；下拉刷新传 `false` / Whether to switch to loading first; pull to refresh passes `false`.
    /// - Returns: 新任务；下拉刷新要等它结束 / The new task, which pull to refresh waits for.
    @discardableResult
    private func loadGiveaways(showsLoading: Bool) -> Task<Void, Never> {
        giveawaysTask?.cancel()
        let task = Task {
            await gamesViewModel.updateGiveaways(showsLoading: showsLoading)
        }
        giveawaysTask = task
        return task
    }

    // MARK: - 收藏 / Favourites

    /// 是否已在 wishlist；没有 appID 的条目不可能收藏。
    /// Whether the row is on the wishlist; rows without an appID never are.
    private func isFavorite(_ item: HomeDealItem) -> Bool {
        guard let appID = item.steamAppID else { return false }
        return wishlistService.contains(appID: appID)
    }

    /// 切换收藏；未登录先弹登录 sheet。
    /// Toggles the favourite; signed-out users get the sign-in sheet first.
    private func toggleFavorite(_ item: HomeDealItem) {
        guard let appID = item.steamAppID else { return }
        guard let uid = authService.uid else {
            isShowingSignIn = true
            return
        }

        if wishlistService.contains(appID: appID) {
            wishlistService.remove(appID: appID, uid: uid)
        } else {
            // 用户正看着这个折扣，记成已知低价，提醒就不会再推同一次打折。
            // The user is looking at this sale, so it becomes the known low and the alert won't push the same sale again.
            let dealInfo = item.deal.deal
            let knownLow = WishlistItem.KnownPrice(price: dealInfo.price, cut: dealInfo.cut, region: region)
            let wishlistItem = WishlistItem(
                appID: appID,
                name: item.deal.title,
                // 要和详情页存的 Steam header 同比例，Wishlist 行才能完整显示。
                // Same ratio as the Steam header the detail page saves, so wishlist rows show it whole.
                headerImageURL: item.deal.headerRatioArtworkURL?.absoluteString,
                itadID: item.deal.id,
                // 首页只有 ITAD 的 deal，拿不到 Steam 的发售状态；Wishlist 页会核对后回写。
                // The home page only has ITAD's deal, not Steam's release status; the wishlist page checks and writes it back.
                isComingSoon: nil,
                releaseCheckedAt: nil,
                knownLow: knownLow
            )
            // 只会抛编码错误，固定结构的模型不会发生；服务器拒绝由监听器回滚。
            // Only encoding errors can throw, which this fixed model never hits; server rejections are rolled back by the listener.
            try? wishlistService.add(wishlistItem, uid: uid)

            // 第一次收藏时请求通知权限，这时用户看得懂提醒是干什么用的。
            // Asks for notification permission on the first favourite, when the purpose of alerts is clear.
            Task {
                await NotificationPermission.requestIfNeeded()
            }
        }
    }

    // MARK: - 进详情页 / Opening the detail page

    /// 点卡片：按 Steam 关联的四种状态决定跳转还是提示。
    /// Taps a card: jumps or warns according to the four Steam link states.
    ///
    /// 一个 `Int?` 区分不了「还在查」「查过没有」与「查失败了」，所以按四态分。
    /// A single `Int?` cannot tell "still resolving", "has none" and "lookup failed" apart, hence four states.
    private func handleTap(_ item: HomeDealItem) {
        switch item.steam {
        case .pending:
            // 补 appID 几百毫秒就回来，这期间静默忽略。
            // Resolving lands within a few hundred milliseconds; taps in between are ignored.
            break

        case .resolved(let appID):
            // 赋值即跳转：`item:` 非空就推栈。
            // Assigning navigates: `.navigationDestination(item:)` pushes once non-nil.
            homeDetailItem = GameDetailRoute(appID: appID, uuid: item.itadUUID, title: item.deal.title)

        case .notFound:
            warning = "“\(item.deal.title)” is not available on Steam, so there is no detail page to show."

        // 查失败不代表 Steam 上没有，提示下拉刷新重查。
        // A failed lookup does not mean the game is missing; point to pull to refresh.
        case .failed:
            warning = "Couldn't check “\(item.deal.title)” on Steam. Pull down to refresh and try again."
        }
    }

    // MARK: - 区块标题 / Section titles

    /// 「Hot Deals」区块标题，整个标题可点，推入全部 Hot Deals。
    /// Section title for the Hot Deals block; the whole title is tappable and pushes the full list.
    ///
    /// 同 App Store 的区块标题：标题后跟灰色箭头表示「查看全部」，不另放文字按钮。
    /// Matches App Store shelf titles: a grey chevron after the title means See All, with no separate text button.
    @ViewBuilder
    private var hotDealsHeader: some View {
        HStack(spacing: 0) {
            Button {
                hotDealsRoute = HotDealsRoute()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "flame.fill")
                        .font(.title2)
                        .foregroundStyle(.orange)

                    HStack(spacing: 6) {
                        Text("Hot Deals")
                            .font(.system(.title, weight: .bold))
                            .foregroundStyle(.primaryText)

                        Image(systemName: "chevron.right")
                            .font(.system(.title3, weight: .bold))
                            .foregroundStyle(.secondaryText)
                    }
                }
            }
            // `.plain` 保留标题自己的颜色，不被 tint 染色。
            // `.plain` keeps the title's own colours instead of the tint.
            .buttonStyle(.plain)
            // 只读标题，不读两个图标的名字。
            // Reads just the title, not the two symbol names.
            .accessibilityLabel("Hot Deals")
            .accessibilityAddTraits(.isHeader)
            .accessibilityHint("Shows all hot deals")

            Spacer(minLength: 0)
        }
        .padding(.bottom, 2)
    }

    /// 「Giveaways」区块标题。
    /// Section title for the Giveaways block.
    @ViewBuilder
    private var giveawayHeader: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                // 装饰图标，只读标题，同 Hot Deals。
                // Decorative; only the title is read, as with Hot Deals.
                Image(systemName: "gift.fill")
                    .font(.title2)
                    .foregroundStyle(.brandGreen)
                    .accessibilityHidden(true)

                Text("Giveaways")
                    .font(.system(.title, weight: .bold))
                    .foregroundStyle(.primaryText)
                    .accessibilityAddTraits(.isHeader)

                Spacer(minLength: 0)
            }
        }
        // 区块间留白，与上一区块拉开距离。
        // The gap above the section, separating it from the previous block.
        .padding(.top, 20)
        .padding(.bottom, 2)
    }
}

#Preview {
    // 预览不走 `AppDelegate`，直接连真实 Firebase。
    // Previews skip `AppDelegate`, so connect to the real Firebase here.
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()
    // 复刻 `MainTabView`：`navigationDestination` 只在 `NavigationStack` 里生效。
    // Mirrors `MainTabView`; the modifier needs a `NavigationStack`.
    NavigationStack {
        GamesView()
    }
    .modelContainer(for: UserSettingModel.self, inMemory: true)
    .environment(AuthService())
    .environment(WishlistService())
}
