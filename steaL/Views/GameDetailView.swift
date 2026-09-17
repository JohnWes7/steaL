//
//  GameDetailView.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import SwiftData
import SwiftUI

/// 游戏详情页：数据由 `GameDetailViewModel` 加载，Steam 详情是硬门槛，ITAD 比价与评分随后补齐 /
/// Game detail page; `GameDetailViewModel` loads the Steam details first as the hard
/// requirement, with the ITAD price and info sections filling in afterwards
struct GameDetailView: View {

    /// 设置单例，用来决定请求的区域与语言 / The settings singleton, which decides region and language
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    @State private var gameDetailViewModel: GameDetailViewModel
    @State private var isSteamDetailExpanded = false
    @State private var offerFilter: OfferFilter = .steam
    @State private var isShowingAllOffers = false

    /// 完整游戏的路由，非空即推入 / The full game's route; a push happens once it is non-nil
    @State private var fullGameRoute: GameDetailRoute?

    /// 用导航参数创建详情页及其状态机 / Creates the detail page and its state machine from the route
    init(route: GameDetailRoute) {
        _gameDetailViewModel = State(initialValue: GameDetailViewModel(route: route))
    }

    /// 当前区域 / Current region
    private var region: AppRegion { settings.first?.region ?? .australia }

    /// 当前语言 / Current language
    private var language: AppLanguage { settings.first?.language ?? .english }

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
        // 用 `.task(id:)`：参数变化自动重跑，离开时自动取消在飞请求 / `.task(id:)` re-runs on a parameter change and cancels on disappear
        .task(id: reloadKey) {
            await gameDetailViewModel.load(region: region, language: language)
        }
        .toolbar(.visible, for: .navigationBar)
        .navigationTitle(navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        // 详情页自己注册跳转目标，从被推入的页面再推一页不靠外层栈 / Register our own destination; the enclosing stack needs no `for:`
        .navigationDestination(item: $fullGameRoute) { route in
            GameDetailView(route: route)
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

        case .failed(let error):
            ContentUnavailableView {
                Label(error.title, systemImage: error.symbolName)
            } description: {
                Text(error.message)
            } actions: {
                Button("Try Again") {
                    Task {
                        await gameDetailViewModel.load(
                            region: region,
                            language: language,
                            isRetry: true
                        )
                    }
                }
                .tint(.brandGreen)
            }
        }
    }

    /// 数据就绪后的内容：画廊 + 详情区块 / The page once the data is in: gallery plus the detail sections
    private func detailContent(_ details: SteamAppDetails) -> some View {
        let galleryImageURLs = ([details.headerImage] + (details.screenshots ?? []).compactMap(\.full))
            .compactMap { $0 }
        

        return ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 0) {
                MediaGalleryView(imageURLs: galleryImageURLs, movies: details.movies ?? [])

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
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(.brandGreen)

                Text(details.isComingSoon ? "Coming soon · \(date)" : "Released · \(date)")
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
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
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
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
}

#Preview("搜索路径 / Search path") {
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
}
