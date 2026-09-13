//
//  GameDetailView.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import SwiftData
import SwiftUI

/// 游戏详情页 / Game detail page
///
/// 数据由 `GameDetailViewModel` 加载：先拿 Steam 详情（硬门槛），ITAD 的比价与评分等接入时
/// 继续往同一个 `load` 里加 /
/// Data is loaded by `GameDetailViewModel`: Steam details first (the hard requirement), with the
/// ITAD price and info steps joining the same `load` once their DTOs exist
struct GameDetailView: View {

    /// 导航携带的参数 / The payload handed over by the navigation stack
    let route: GameDetailRoute

    /// 设置单例，用来决定请求的区域与语言 / The settings singleton, which decides region and language
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    @State private var gameDetailViewModel = GameDetailViewModel()
    @State private var isSteamDetailExpanded = false

    /// 用户开了「减弱动态效果」时跳过展开动画 /
    /// Skips the expand animation when Reduce Motion is turned on
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// 当前区域 / Current region
    private var region: AppRegion { settings.first?.region ?? .australia }

    /// 当前语言 / Current language
    private var language: AppLanguage { settings.first?.language ?? .english }

    struct ReloadKey: Equatable, Hashable {
        let route: GameDetailRoute
        let region: AppRegion
        let language: AppLanguage
    }

    /// 参数签名：route 或设置变了就重新加载 /
    /// Parameter signature; a change to the route or the settings triggers a reload
    private var reloadKey: ReloadKey {
        ReloadKey(route: route, region: region, language: language)
    }

    var body: some View {
        ZStack {
            Color.appBackground
                .ignoresSafeArea()

            content
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        // 参数由视图传入，`@State` 里的 view model 只接收调用 /
        // The view hands the parameters in; the `@State` view model only receives calls
        //
        // 用 `.task(id:)` 而不是 `onAppear` + `hasStarted`：详情页没有「切 Tab 不能被取消」的需求，
        // 而 `.task` 在参数变化时自动重跑、视图离开时自动取消在飞请求 /
        // `.task(id:)` rather than `onAppear` + a started flag: unlike the feed, the detail page
        // wants the request cancelled when it goes away, and to re-run when parameters change
        .task(id: reloadKey) {
            await gameDetailViewModel.load(route: route, region: region, language: language)
        }
        .toolbar(.visible, for: .navigationBar)
        .navigationTitle(navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var navigationTitle: String {
        if case .loaded(let details) = gameDetailViewModel.state,
           let name = details.name,
           !name.isEmpty {
            return name
        }

        return route.title
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
                            route: route,
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

    /// 数据就绪后的内容 / The page once the data is in
    ///
    /// 目前只用到 Steam 那一半加上一块临时比价；截图、视频与完整价格区后续往里加 /
    /// The Steam half plus a provisional price block for now; screenshots, movies and the real
    /// price section come next
    private func detailContent(_ details: SteamAppDetails) -> some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 0) {
                mediaGallery(details)

                VStack(alignment: .leading, spacing: 22) {
                    steamReleaseSection(details)

                    steamDetailSection(details)

                    infoSection
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 32)
            }
        }
    }

    /// Steam 图片分页，第一张使用商店主图，随后展示 Steam 截图 /
    /// Steam media paging: the store header image first, followed by Steam screenshots
    @ViewBuilder
    private func mediaGallery(_ details: SteamAppDetails) -> some View {
        let imageURLs = ([details.headerImage] + (details.screenshots ?? []).compactMap(\.full))
            .compactMap { $0 }

        if imageURLs.isEmpty {
            Color.surfaceElevated
                .frame(maxWidth: .infinity)
                .aspectRatio(16 / 9, contentMode: .fit)
                .overlay {
                    Image(systemName: "photo")
                        .font(.system(size: 30, weight: .medium))
                        .foregroundStyle(.secondaryText)
                }
        } else {
            TabView {
                ForEach(Array(imageURLs.enumerated()), id: \.offset) { _, imageURL in
                    AsyncImage(url: URL(string: imageURL)) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .scaledToFill()
                        case .failure:
                            Color.surfaceElevated
                                .overlay {
                                    Image(systemName: "photo.badge.exclamationmark")
                                        .font(.system(size: 30, weight: .medium))
                                        .foregroundStyle(.secondaryText)
                                }
                        case .empty:
                            Color.surfaceElevated
                                .overlay {
                                    ProgressView()
                                        .tint(.brandGreen)
                                }
                        @unknown default:
                            Color.surfaceElevated
                        }
                    }
                    .clipped()
                }
            }
            .tabViewStyle(.page(indexDisplayMode: imageURLs.count > 1 ? .automatic : .never))
            .frame(maxWidth: .infinity)
            .aspectRatio(16 / 9, contentMode: .fit)
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

    /// Steam 简介、开发商与发行商的折叠区：平铺在页面上，不用卡片包裹 /
    /// Collapsible Steam description and credits, laid out flat rather than inside a card
    ///
    /// 收起时简介只露三行，蓝色 `More ⌄` 贴在最后一行的右端；没被截断时它退到文字下方 /
    /// Collapsed, the description shows three lines with a blue `More ⌄` riding the end of the last
    /// one; when nothing is truncated the control drops below the text instead
    @ViewBuilder
    private func steamDetailSection(_ details: SteamAppDetails) -> some View {
        let developers = details.developers?.filter { !$0.isEmpty } ?? []
        let publishers = details.publishers?.filter { !$0.isEmpty } ?? []

        if let description = details.shortDescription {
            VStack(alignment: .leading, spacing: 14) {
                Text("GAME DETAIL")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.brandGreen)

                if isSteamDetailExpanded {
                    descriptionText(description)

                    creditsList(developers: developers, publishers: publishers)

                    HStack(spacing: 0) {
                        Spacer(minLength: 0)

                        Button {
                            toggleSteamDetail()
                        } label: {
                            toggleLabel("Show less", isExpanded: true)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Show less")
                    }
                } else {
                    descriptionText(description, lineLimit: 3)
                        .overlay(alignment: .bottomTrailing) {
                            inlineMoreButton
                        }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else if !developers.isEmpty || !publishers.isEmpty {
            VStack(alignment: .leading, spacing: 14) {
                Text("GAME DETAIL")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.brandGreen)

                creditsList(developers: developers, publishers: publishers)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// 贴在简介最后一行右端的展开开关，左侧渐变让底下的文字自然淡出，避免生硬的截断 /
    /// The expand control riding the end of the description's last line; the gradient fades the text
    /// out underneath instead of cutting it off
    private var inlineMoreButton: some View {
        Button {
            toggleSteamDetail()
        } label: {
            toggleLabel("More", isExpanded: false)
                .padding(.leading, 36)
                .padding(.vertical, 3)
                .background(
                    LinearGradient(
                        stops: [
                            .init(color: .appBackground.opacity(0), location: 0),
                            .init(color: .appBackground, location: 0.35),
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Show more")
    }

    /// 简介正文的统一样式 / Shared styling for the description body
    private func descriptionText(_ text: String, lineLimit: Int? = nil) -> some View {
        Text(text)
            .font(.system(size: 17, weight: .regular, design: .rounded))
            .foregroundStyle(.secondaryText)
            .lineSpacing(5)
            .lineLimit(lineLimit)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// 厂商信息，展开后才出现 / The credits, shown once the section is expanded
    @ViewBuilder
    private func creditsList(developers: [String], publishers: [String]) -> some View {
        if !developers.isEmpty {
            infoRow(label: "Developer", value: developers.joined(separator: ", "))
        }
        if !publishers.isEmpty {
            infoRow(label: "Publisher", value: publishers.joined(separator: ", "))
        }
    }

    /// 蓝色 `More ⌄` / `Show less ⌃`，文字和箭头始终挨在一起；收起向下、展开向上 /
    /// The blue `More ⌄` and `Show less ⌃`, with the label and its chevron always together; down
    /// while collapsed, up while expanded
    private func toggleLabel(_ title: String, isExpanded: Bool) -> some View {
        HStack(spacing: 4) {
            Text(title)
                .font(.system(size: 15, weight: .semibold, design: .rounded))

            Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                .font(.system(size: 12, weight: .bold))
        }
        .foregroundStyle(.brandBlue)
    }

    /// 展开与收起；开了减弱动态效果就直接切换，不带动画 /
    /// Toggles the section; with Reduce Motion on it switches without the transition
    private func toggleSteamDetail() {
        if reduceMotion {
            isSteamDetailExpanded.toggle()
        } else {
            withAnimation(.snappy(duration: 0.3)) {
                isSteamDetailExpanded.toggle()
            }
        }
    }

    /// ITAD 评分、厂商、标签与热度 / ITAD reviews, credits, tags and popularity
    @ViewBuilder
    private var infoSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("ITAD details")
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(.brandGreen)

            switch gameDetailViewModel.infoState {
            case .idle:
                EmptyView()

            case .loading:
                ProgressView()
                    .controlSize(.small)

            case .loaded(let info):
                if let steam = info.steamReview, let score = steam.score {
                    infoRow(label: "Steam", value: "\(score)%")
                }
                if let metascore = info.reviews?.first(where: { $0.source == "Metascore" })?.score {
                    infoRow(label: "Metascore", value: "\(metascore)")
                }
                if let openCritic = info.reviews?.first(where: { $0.source == "OpenCritic" })?.score {
                    infoRow(label: "OpenCritic", value: "\(openCritic)")
                }
                if let developer = info.primaryDeveloper {
                    infoRow(label: "Developer", value: developer)
                }
                if let publisher = info.primaryPublisher {
                    infoRow(label: "Publisher", value: publisher)
                }
                if let tags = info.tags, !tags.isEmpty {
                    infoRow(label: "Tags", value: tags.joined(separator: ", "))
                }
                if let recent = info.players?.recent, recent > 0 {
                    infoRow(label: "Players", value: recent.formatted())
                }
                if let rank = info.stats?.rank, rank > 0 {
                    infoRow(label: "Rank", value: "#\(rank)")
                }

            case .unavailable:
                Text("No info data")
                    .font(.system(size: 14))
                    .foregroundStyle(.secondaryText)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    /// 一行信息 / One info row
    private func infoRow(label: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(label)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.secondaryText)
                .frame(width: 96, alignment: .leading)

            Text(value)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(.primaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// 临时的比价区块：只验证数据接上没有，版式后续重做 /
    /// Provisional price block: only proves the data is wired up; the layout comes later
    ///
    /// 比价晚于详情到达，所以它自己带一个小加载态，不让整页回到转圈 /
    /// The prices arrive after the details, so the section carries its own little loading state
    /// instead of sending the whole page back to a spinner
    @ViewBuilder
    private var priceSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("ITAD PRICES")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.brandGreen)

            switch gameDetailViewModel.pricesState {
            case .idle:
                EmptyView()

            case .loading:
                ProgressView()
                    .controlSize(.small)

            case .loaded(let prices):
                ForEach(Array(prices.steamDeals.enumerated()), id: \.offset) { _, deal in
                    priceRow(
                        label: deal.shop.name,
                        price: deal.price,
                        detail: "-\(deal.cut)%"
                    )
                }

                if let low = prices.allTimeLow {
                    priceRow(label: "All-time low", price: low, detail: nil)
                }

            case .unavailable:
                Text("No price data")
                    .font(.system(size: 14))
                    .foregroundStyle(.secondaryText)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    /// 一行价格 / One price line
    private func priceRow(label: String, price: ITADPrice, detail: String?) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(label)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.secondaryText)
                .frame(width: 96, alignment: .leading)

            // 货币代码取数据自带的那个，不硬编码区域 /
            // The currency code comes from the payload rather than a hard-coded region
            Text(price.amount.formatted(.currency(code: price.currency)))
                .font(.system(size: 18, weight: .heavy, design: .rounded))
                .foregroundStyle(.brandGreen)

            if let detail {
                Text(detail)
                    .font(.system(size: 13))
                    .foregroundStyle(.secondaryText)
            }
        }
    }

    /// 参数回显。`uuid` 为空时显式标注，方便区分「主页路径」和「搜索路径」/
    /// Parameter echo. A missing `uuid` is spelled out so the home path and the search path are
    /// easy to tell apart
    private var parameterList: some View {
        VStack(alignment: .leading, spacing: 12) {
            row(label: "appID", value: String(route.appID), isMissing: false)
            row(label: "uuid", value: route.uuid ?? "—", isMissing: route.uuid == nil)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    /// 单行参数 / One parameter line
    private func row(label: String, value: String, isMissing: Bool) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(label)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.secondaryText)
                .frame(width: 64, alignment: .leading)

            Text(value)
                .font(.system(size: 16, weight: .medium, design: .monospaced))
                .foregroundStyle(isMissing ? .secondaryText : .primaryText)
                .textSelection(.enabled)
                .fixedSize(horizontal: false, vertical: true)
        }
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
