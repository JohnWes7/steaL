//
//  GameDetailView.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import FirebaseCore
import SwiftData
import SwiftUI
import TipKit

/// 游戏详情页：数据由 `GameDetailViewModel` 加载，Steam 详情是硬门槛，ITAD 比价与评分随后补齐 /
/// Game detail page; `GameDetailViewModel` loads the Steam details first as the hard
/// requirement, with the ITAD price and info sections filling in afterwards
struct GameDetailView: View {

    /// 设置单例，用来决定请求的区域与语言 / The settings singleton, which decides region and language
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    /// 登录状态，收藏要用 uid / Sign-in state; favouriting needs the uid
    @Environment(AuthService.self) private var authService

    /// 当前账号的 wishlist，用来判断心形是否已收藏 / The current account's wishlist, for the heart's filled state
    @Environment(WishlistService.self) private var wishlistService

    /// 所在场景的前后台状态，回到前台时检查比价是否过期。
    /// The enclosing scene's phase; a return to the foreground checks whether the prices are stale.
    @Environment(\.scenePhase) private var scenePhase

    @State private var gameDetailViewModel: GameDetailViewModel

    @State private var aiAdviceViewModel: AIAdviceViewModel = AIAdviceViewModel()

    // view state
    @State private var isSteamDetailExpanded = false
    @State private var offerFilter: OfferFilter = .steam
    @State private var isShowingAllOffers = false

    /// 未登录点心形时弹出登录 sheet / Shows the sign-in sheet when a signed-out user taps the heart
    @State private var isShowingSignIn = false

    /// AI 建议的弹窗 / The AI advice sheet
    @State private var isShowingAIAdvice = false

    /// 完整游戏的路由，非空即推入 / The full game's route; a push happens once it is non-nil
    @State private var fullGameRoute: GameDetailRoute?

    /// ✨ 按钮上的提示，第一次看到就显示，点过 ✨ 后不再出现。
    /// The tip on the ✨ button; shows the first time and never again once ✨ has been tapped.
    private let aiAdviceTip = AIAdviceTip()

    /// 用导航参数创建详情页及其状态机 / Creates the detail page and its state machine from the route
    init(route: GameDetailRoute) {
        _gameDetailViewModel = State(initialValue: GameDetailViewModel(route: route))
    }

    /// 当前区域 / Current region
    private var region: AppRegion { settings.first?.region ?? .australia }

    /// 当前语言 / Current language
    private var language: AppLanguage { settings.first?.language ?? .english }

    /// AI 建议要用的模型。
    /// The model the AI advice uses.
    ///
    /// 设置里选中的自己的模型；`nil` 是内置。
    /// The user's own model chosen in settings; `nil` means the built-in one.
    private var selectedLLMModel: LLMModel? { settings.first?.llmModel }

    struct ReloadKey: Equatable, Hashable {
        let region: AppRegion
        let language: AppLanguage
    }

    /// 参数签名：route 或设置变了就重新加载 /
    /// Parameter signature; a change to the route or the settings triggers a reload
    private var reloadKey: ReloadKey {
        ReloadKey(region: region, language: language)
    }

    var body: some View {
        ZStack {
            Color.appBackground
                .ignoresSafeArea()

            content
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        // 用 `.task(id:)`：参数变化自动重跑，离开时自动取消在飞请求；页面重新出现不重载 /
        // `.task(id:)` re-runs on a parameter change and cancels on disappear; reappearing does not reload
        .task(id: reloadKey) {
            await gameDetailViewModel.loadIfNeeded(region: region, language: language)
        }
        // 回到前台（以及页面重新出现）时，比价过期就静默重查；没过期、没显示比价都什么都不做 /
        // On a return to the foreground (or a reappearance), quietly refetch stale prices; otherwise do nothing
        .task(id: scenePhase) {
            guard scenePhase == .active else { return }
            await gameDetailViewModel.refreshPricesIfStale(region: region)
        }
        .toolbar(.visible, for: .navigationBar)
        .navigationTitle(navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        // 详情页自己注册跳转目标，从被推入的页面再推一页不靠外层栈 / Register our own destination; the enclosing stack needs no `for:`
        .navigationDestination(item: $fullGameRoute) { route in
            GameDetailView(route: route)
        }
        .sheet(isPresented: $isShowingSignIn) {
            SignInSheet()
        }
        .sheet(isPresented: $isShowingAIAdvice) {
            AIAdviceSheet(
                viewModel: aiAdviceViewModel,
                pricesState: gameDetailViewModel.pricesState,
                regenerate: regenerateAIAdvice
            )
        }
    }

    private var navigationTitle: String {
        gameDetailViewModel.title
    }

    /// 按状态切换的页面主体 / The page body, switched on the load state
    @ViewBuilder
    private var content: some View {
        switch gameDetailViewModel.state {
        case .loading:
            ProgressView()
                .tint(.brandGreen)
                .frame(maxWidth: .infinity)

        case .loaded(let details):
            detailContent(details)

        // 查了没有：不是失败，不走 `LoadErrorView`、不给重试 / Checked and missing: not a failure, so no `LoadErrorView` and no retry
        case .notFound:
            ContentUnavailableView(
                "Game Unavailable",
                systemImage: "questionmark.app",
                description: Text("This game isn't on the Steam store in your region, or it has been removed.")
            )

        case .failed(let error):
            LoadErrorView(error: error) {
                Task {
                    await gameDetailViewModel.load(
                        region: region,
                        language: language,
                        isRetry: true
                    )
                }
            }
        }
    }

    /// 数据就绪后的内容：画廊 + 详情区块 / The page once the data is in: gallery plus the detail sections
    private func detailContent(_ details: SteamAppDetails) -> some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 0) {
                MediaGalleryView(
                    headerImageURL: details.headerImage,
                    movies: details.movies ?? [],
                    screenshotURLs: (details.screenshots ?? []).compactMap(\.full)
                )

                VStack(alignment: .leading, spacing: 22) {
                    genreChips(details)

                    steamReleaseSection(details)

                    SteamDetailSectionView(
                        details: details,
                        isExpanded: $isSteamDetailExpanded
                    )

                    if let demo = demoLink(details) {
                        DemoSectionView(appID: demo.appID, name: demo.name)
                    }

                    if let fullGame = fullGameLink(details) {
                        FullGameSectionView(name: fullGame.title) {
                            fullGameRoute = fullGame
                        }
                    }

                    // 该不该出现由 ViewModel 定成 `.hidden`，区块自己不渲染 / The view model decides via `.hidden`
                    PriceSectionView(
                        state: gameDetailViewModel.pricesState,
                        offerFilter: $offerFilter,
                        isShowingAllOffers: $isShowingAllOffers,
                        retry: retryPrices
                    )

                    ITADInfoSectionView(
                        state: gameDetailViewModel.infoState,
                        retry: retryInfo
                    )

                    DetailFooterView(
                        appID: gameDetailViewModel.appID,
                        itadID: gameDetailViewModel.uuid
                    )
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 32)
            }
        }
        // 挂在加载完成的内容上：加载中与失败页不出现心形 / Attached to the loaded content, so loading and error pages show no heart
        .toolbar {
            // 和心形同在右上角、共用一块玻璃，心形最常用放最右 / Shares the trailing glass group with the heart, which stays outermost as the most used
            // 拼得出 AI 的输入才有入口，条件由 ViewModel 定 / The entry shows only when the view model can build the AI input
            if let adviceContext = gameDetailViewModel.adviceContext {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("AI Advice", systemImage: "sparkles") {
                        showAIAdvice(context: adviceContext)
                    }
                    // iOS 26 导航栏里只有图标的按钮挂不出 popover tip，加一个按钮样式才显示（Apple 论坛的已知问题）；
                    // `.plain` 不用 tint 上色，颜色改用 `foregroundStyle`。
                    // On iOS 26 a popover tip won't show on an icon-only toolbar button until it has a button style
                    // (a known issue on the Apple forums); `.plain` ignores tint, so the colour comes from `foregroundStyle`.
                    .buttonStyle(.plain)
                    .foregroundStyle(.brandBlue)
                    .accessibilityHint("Suggests whether to buy now or wait, based on prices and reviews")
                    .popoverTip(aiAdviceTip)
                }
            }

            if showsWishlistButton(details) {
                ToolbarItem(placement: .topBarTrailing) {
                    wishlistButton(details)
                }
            }
        }
    }

    // MARK: - AI 建议 / AI advice

    /// 打开 sheet 并在需要时生成；请求从这里发而不是 sheet 的 `.task`，关掉 sheet 不会取消它 /
    /// Opens the sheet and generates if needed; the request starts here rather than in the sheet's `.task`,
    /// so closing the sheet does not cancel it
    private func showAIAdvice(context: DetailAIAdviceContext) {
        isShowingAIAdvice = true

        // 用过这个功能就不用再提示。
        // Once the feature has been used, the tip is no longer needed.
        aiAdviceTip.invalidate(reason: .actionPerformed)

        Task {
            await aiAdviceViewModel.generateIfNeeded(context: context, language: language, model: selectedLLMModel)
        }
    }

    /// sheet 里的 Retry / Regenerate：用最新的数据重新拼输入；拼不出来（如信息区块正在重试）就不动 /
    /// The sheet's Retry and Regenerate: rebuilds the input from fresh data, doing nothing while it cannot be built
    private func regenerateAIAdvice() {
        guard let context = gameDetailViewModel.adviceContext else { return }

        Task {
            await aiAdviceViewModel.regenerate(context: context, language: language, model: selectedLLMModel)
        }
    }

    // MARK: - 收藏 / Favourites

    /// 是否已在 wishlist / Whether the game is on the wishlist
    private var isFavorite: Bool {
        wishlistService.contains(appID: gameDetailViewModel.appID)
    }

    /// 要不要显示心形：已收藏的总是显示，免得在详情页取消不了 /
    /// Whether to show the heart; a wishlisted game always shows it so it can still be removed here
    private func showsWishlistButton(_ details: SteamAppDetails) -> Bool {
        if isFavorite {
            return true
        }

        return details.gameKind.canWishlist
    }

    /// 导航栏的收藏按钮 / The favourite button in the navigation bar
    private func wishlistButton(_ details: SteamAppDetails) -> some View {
        Button {
            toggleFavorite(details)
        } label: {
            Label(
                isFavorite ? "Remove from Wishlist" : "Add to Wishlist",
                systemImage: isFavorite ? "heart.fill" : "heart"
            )
        }
        // 已收藏用粉色，与首页心形一致；未收藏跟随系统默认 / Pink when saved, matching the home hearts; system default otherwise
        .tint(isFavorite ? Color.pink : nil)
        .contentTransition(.symbolEffect(.replace))
    }

    /// 切换收藏；未登录先弹登录 sheet，登录后不自动收藏 /
    /// Toggles the favourite; signed-out users get the sign-in sheet first, and nothing is added automatically afterwards
    private func toggleFavorite(_ details: SteamAppDetails) {
        guard let uid = authService.uid else {
            isShowingSignIn = true
            return
        }

        let appID = gameDetailViewModel.appID

        if wishlistService.contains(appID: appID) {
            wishlistService.remove(appID: appID, uid: uid)
        } else {
            // 比价已经到手、Steam 能激活的最低价在打折，就记成已知低价；还在查或没有就算了
            // When prices are in and the best Steam deal is on sale, it becomes the known low; otherwise skip it
            let bestDeal = gameDetailViewModel.bestSteamDeal
            var knownLow: WishlistItem.KnownPrice?
            if let bestDeal {
                knownLow = WishlistItem.KnownPrice(price: bestDeal.price, cut: bestDeal.cut, region: region)
            }
            let wishlistItem = WishlistItem(
                appID: appID,
                // `title` 已经优先用 Steam 的名字，缺了才用路由带来的 / `title` prefers Steam's name, falling back to the route's
                name: gameDetailViewModel.title,
                headerImageURL: details.headerImage,
                // UUID 可能还在查，nil 也能存 / The UUID may still be resolving; nil is fine to store
                itadID: gameDetailViewModel.uuid,
                // 不用 `gameKind == .unreleased`：未发售的 demo 算 `.demo`，不算 `.unreleased`；Wishlist 核对也用 `isComingSoon` /
                // Not `gameKind == .unreleased`: an unreleased demo counts as `.demo`, not `.unreleased`; the wishlist check uses `isComingSoon` too
                isComingSoon: details.isComingSoon,
                // 刚拿到 Steam 详情，等于刚核对过 / Steam details just arrived, so this counts as a fresh check
                releaseCheckedAt: .now,
                knownLow: knownLow
            )
            // 只会抛编码错误，固定结构的模型不会发生；服务器拒绝由监听器回滚 /
            // Only encoding errors can throw, which this fixed model never hits; server rejections are rolled back by the listener
            try? wishlistService.add(wishlistItem, uid: uid)

            // 第一次收藏时请求通知权限，这时用户看得懂提醒是干什么用的
            // Asks for notification permission on the first favourite, when the purpose of alerts is clear
            Task {
                await NotificationPermission.requestIfNeeded()
            }
        }
    }

    /// 图片下方的 Steam 类型标签 / Steam genre chips below the media gallery
    @ViewBuilder
    private func genreChips(_ details: SteamAppDetails) -> some View {
        let genres = details.genres?.compactMap(\.description).filter { !$0.isEmpty } ?? []

        if !genres.isEmpty {
            chipRow(genres)
        }
    }

    /// 只展示 Steam 的发售事实，避免与 ITAD 的补充数据混在一起 /
    /// Steam release facts stay separate from the supplementary ITAD data
    @ViewBuilder
    private func steamReleaseSection(_ details: SteamAppDetails) -> some View {
        if let date = details.releaseDate?.date {
            HStack(spacing: 10) {
                Image(systemName: "calendar")
                    .font(.system(.callout, weight: .medium))
                    .foregroundStyle(.brandGreen)

                Text(details.isComingSoon ? "Coming soon · \(date)" : "Released · \(date)")
                    .font(.system(.callout, design: .rounded, weight: .semibold))
                    .foregroundStyle(.primaryText)
            }
        }
    }

    /// 封面下的类型标签可横向滚动 / Genre chips below the cover can scroll horizontally
    private func chipRow(_ labels: [String]) -> some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                ForEach(Array(labels.enumerated()), id: \.offset) { _, label in
                    Text(label)
                        .font(.system(.subheadline, design: .rounded, weight: .semibold))
                        .foregroundStyle(.primaryText)
                        .padding(.horizontal, 12)
                        .frame(height: 32)
                        .background(Color.surfaceElevated, in: Capsule())
                }
            }
        }
        .scrollIndicators(.hidden)
    }

    /// 重试 ITAD 比价 / Retries the ITAD prices
    private func retryPrices() {
        Task {
            await gameDetailViewModel.retryPrices(region: region)
        }
    }

    /// 重试 ITAD 信息 / Retries the ITAD info
    private func retryInfo() {
        Task {
            await gameDetailViewModel.retryInfo()
        }
    }

    /// 试玩版入口要跳到哪个 appid：本体的页面上是 Steam 列的试玩版，试玩版页面上是它自己 /
    /// Which appid the demo entry opens: a demo Steam lists on a base game, or the demo itself
    private func demoLink(_ details: SteamAppDetails) -> (appID: Int, name: String)? {
        if let demo = details.demos?.first, let appID = demo.appid {
            // Steam 很少填试玩版自己的名字（6 个里只 1 个有），空串退回「本体名 + Demo」/ Rarely filled in; empty falls back
            let fallback = "\(gameDetailViewModel.title) Demo"
            return (appID, demo.description.flatMap { $0.isEmpty ? nil : $0 } ?? fallback)
        }

        // 试玩版自己的页面上 `demos` 是空的，标题本身就是试玩版名 / A demo's own page carries no `demos`
        guard details.isDemo,
            let appID = details.steamAppID
        else { return nil }

        return (appID, details.name ?? "Demo")
    }

    /// 完整游戏的入口：只有试玩版条目带 `fullgame`，它的 appid 是**字符串**，要转一次 /
    /// The full game's entry: only demo entries carry `fullgame`, whose appid is a **string**
    private func fullGameLink(_ details: SteamAppDetails) -> GameDetailRoute? {
        guard let fullGame = details.fullgame,
            let appID = fullGame.appid.flatMap(Int.init)
        else { return nil }

        return GameDetailRoute(appID: appID, uuid: nil, title: fullGame.name ?? "Full game")
    }
}

#Preview("主页路径 / Home path") {
    // 预览不走 `AppDelegate`，直接连真实 Firebase / Previews skip `AppDelegate`, so connect to the real Firebase here
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()
    NavigationStack {
        GameDetailView(
            route: GameDetailRoute(
                appID: 620,
                uuid: "018d937f-0f5c-72f6-9b1e-2e5b3c4d5e6f",
                title: "Portal 2"
            )
        )
    }
    .modelContainer(for: UserSettingModel.self, inMemory: true)
    .environment(AuthService())
    .environment(WishlistService())
}

#Preview("搜索路径 / Search path") {
    // 预览不走 `AppDelegate`，直接连真实 Firebase / Previews skip `AppDelegate`, so connect to the real Firebase here
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()
    NavigationStack {
        GameDetailView(
            route: GameDetailRoute(
                appID: 620,
                uuid: nil,
                title: "Portal 2"
            )
        )
    }
    .modelContainer(for: UserSettingModel.self, inMemory: true)
    .environment(AuthService())
    .environment(WishlistService())
}

#Preview("AI 建议提示 / AI advice tip") {
    // 预览不走 `AppDelegate`，直接连真实 Firebase / Previews skip `AppDelegate`, so connect to the real Firebase here
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()
    // 预览不走 `steaLApp.init`：在这里配置 TipKit，并强制显示提示（即使之前关过或点过 ✨）。
    // Previews skip `steaLApp.init`: configure TipKit here and force the tip to show even if it was closed or used.
    let _ = {
        Tips.showAllTipsForTesting()
        try? Tips.configure()
    }()
    NavigationStack {
        GameDetailView(
            route: GameDetailRoute(
                appID: 620,
                uuid: "018d937f-0f5c-72f6-9b1e-2e5b3c4d5e6f",
                title: "Portal 2"
            )
        )
    }
    .modelContainer(for: UserSettingModel.self, inMemory: true)
    .environment(AuthService())
    .environment(WishlistService())
}
