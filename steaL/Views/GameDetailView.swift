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

    /// 设置单例，用来决定请求的区域与语言 / The settings singleton, which decides region and language
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    @State private var gameDetailViewModel: GameDetailViewModel
    @State private var isSteamDetailExpanded = false
    @State private var offerFilter: OfferFilter = .steam

    /// 用导航参数创建详情页及其状态机 / Creates the detail page and its state machine from the route
    init(route: GameDetailRoute) {
        _gameDetailViewModel = State(initialValue: GameDetailViewModel(route: route))
    }

    @Environment(\.openURL) private var openURL

    private enum OfferFilter: String, CaseIterable {
        case steam = "Steam"
        case all = "All"
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
        // 用 `.task(id:)` 而不是 `onAppear` + `hasStarted`：详情页没有「切 Tab 不能被取消」的需求，
        // 而 `.task` 在参数变化时自动重跑、视图离开时自动取消在飞请求 /
        // `.task(id:)` rather than `onAppear` + a started flag: unlike the feed, the detail page
        // wants the request cancelled when it goes away, and to re-run when parameters change
        .task(id: reloadKey) {
            await gameDetailViewModel.load(region: region, language: language)
        }
        .toolbar(.visible, for: .navigationBar)
        .navigationTitle(navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
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

    /// 数据就绪后的内容 / The page once the data is in
    ///
    /// 目前只用到 Steam 那一半加上一块临时比价；截图、视频与完整价格区后续往里加 /
    /// The Steam half plus a provisional price block for now; screenshots, movies and the real
    /// price section come next
    private func detailContent(_ details: SteamAppDetails) -> some View {
        let galleryImageURLs = ([details.headerImage] + (details.screenshots ?? []).compactMap(\.full))
            .compactMap { $0 }

        return ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 0) {
                MediaGalleryView(imageURLs: galleryImageURLs)

                VStack(alignment: .leading, spacing: 22) {
                    genreChips(details)

                    steamReleaseSection(details)

                    SteamDetailSectionView(
                        details: details,
                        isExpanded: $isSteamDetailExpanded
                    )

                    priceSection

                    infoSection
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

    /// ITAD 评分与热度 / ITAD reviews and popularity
    @ViewBuilder
    private var infoSection: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("ITAD DETAILS")
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(.brandGreen)

            switch gameDetailViewModel.infoState {
            case .idle:
                EmptyView()

            case .loading:
                ProgressView()
                    .controlSize(.small)

            case .loaded(let info):
                let reviews =
                    info.reviews?.filter {
                        $0.source?.isEmpty == false && $0.score != nil
                    } ?? []

                if !reviews.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Reviews")
                            .font(.system(size: 22, weight: .heavy))
                            .foregroundStyle(.primaryText)

                        ForEach(Array(reviews.enumerated()), id: \.offset) { _, review in
                            reviewRow(review)
                        }
                    }
                }

                let recentPlayers = info.players?.recent.flatMap { $0 > 0 ? $0 : nil }
                let itadRank = info.stats?.rank.flatMap { $0 > 0 ? $0 : nil }

                if recentPlayers != nil || itadRank != nil {
                    HStack(spacing: 0) {
                        if let recentPlayers {
                            itadMetric(
                                icon: "person.3.fill",
                                label: "Players online",
                                value: recentPlayers.formatted(),
                                tint: .brandGreen
                            )
                        }

                        // `recentPlayers` 与 `itadRank` 都有值时加竖线分隔，避免视觉上挤在一起 / When both `recentPlayers` and `itadRank` are available, add a vertical separator to avoid visual crowding
                        if recentPlayers != nil && itadRank != nil {
                            Rectangle()
                                .fill(Color.secondaryText.opacity(0.24))
                                .frame(width: 1, height: 58)
                                .padding(.horizontal, 16)
                        }

                        if let itadRank {
                            itadMetric(
                                icon: "medal.fill",
                                label: "ITAD rank",
                                value: "#\(itadRank)",
                                tint: .brandBlue
                            )
                        }
                    }
                }

            case .unavailable:
                unavailableState("No info data") {
                    Task {
                        await gameDetailViewModel.retryInfo()
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 无数据时的重试提示 / Retry prompt for an unavailable optional section
    private func unavailableState(_ message: String, retry: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(.secondaryText)

            Button("Retry", action: retry)
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(.brandBlue)
                .buttonStyle(.plain)
        }
    }

    /// ITAD 底部指标：图标、重点数字与简短标签 /
    /// Compact ITAD metric with an icon, a prominent value, and a short label
    private func itadMetric(icon: String, label: String, value: String, tint: Color) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(.appBackground)
                .frame(width: 32, height: 32)
                .background(tint, in: RoundedRectangle(cornerRadius: 9, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(value)
                    .font(.system(size: 20, weight: .heavy, design: .rounded))
                    .foregroundStyle(tint)

                Text(label)
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .foregroundStyle(.secondaryText)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label), \(value)")
    }

    /// 一个评分来源：分数、评分人数与视觉比例条 / A review source with score, count, and a visual ratio bar
    private func reviewRow(_ review: ITADReview) -> some View {
        let score = min(max(review.score ?? 0, 0), 100)
        let source = review.source ?? "Unknown"

        return VStack(alignment: .leading, spacing: 9) {
            HStack(alignment: .firstTextBaseline) {
                HStack(spacing: 6) {
                    Image(systemName: "hand.thumbsup.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.brandGreen)

                    Text("\(score)%")
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .foregroundStyle(.brandGreen)
                }

                Spacer(minLength: 12)

                Text(review.count.map { "\($0.formatted()) reviews" } ?? "Review count unavailable")
                    .font(.system(size: 14, weight: .medium, design: .rounded))
                    .foregroundStyle(.secondaryText)
            }

            GeometryReader { proxy in
                HStack(spacing: 3) {
                    Capsule()
                        .fill(Color.brandGreen)
                        .frame(width: proxy.size.width * CGFloat(score) / 100)

                    Capsule()
                        .fill(Color.red.opacity(0.75))
                }
            }
            .frame(height: 7)

            HStack {
                Text(source)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.primaryText)

                Spacer(minLength: 12)

                Text("\(score)/100")
                    .font(.system(size: 14, weight: .medium, design: .rounded))
                    .foregroundStyle(.secondaryText)
            }
        }
        .padding(.vertical, 12)
        .overlay(alignment: .bottom) {
            Divider()
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "\(source), \(score) percent, \(review.count.map { $0.formatted() } ?? "review count unavailable") reviews"
        )
    }

    /// 历史价格窗口 / Historical price windows
    ///
    /// 比价晚于详情到达，所以它自己带一个小加载态，不让整页回到转圈 /
    /// The prices arrive after the details, so the section carries its own little loading state
    /// instead of sending the whole page back to a spinner
    @ViewBuilder
    private var priceSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("PRICES")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.brandGreen)

            switch gameDetailViewModel.pricesState {
            case .idle:
                EmptyView()

            case .loading:
                ProgressView()
                    .controlSize(.small)

            case .free:
                Text("Free")
                    .font(.system(size: 22, weight: .heavy, design: .rounded))
                    .foregroundStyle(.brandGreen)

            case .demo:
                Text("Demo")
                    .font(.system(size: 22, weight: .heavy, design: .rounded))
                    .foregroundStyle(.brandGreen)

            case .loaded(let prices):
                let lows = [
                    ("All-time low", prices.historyLow.all),
                    ("1 year low", prices.historyLow.y1),
                    ("3 months low", prices.historyLow.m3),
                ].compactMap { label, price in
                    price.map { (label, $0) }
                }

                if lows.isEmpty {
                    Text("No historical price data")
                        .font(.system(size: 14))
                        .foregroundStyle(.secondaryText)
                } else {
                    ForEach(Array(lows.enumerated()), id: \.offset) { index, entry in
                        historicalPriceRow(
                            label: entry.0,
                            price: entry.1,
                            accent: priceAccent(for: index)
                        )
                    }
                }

                currentOffers(prices)

            case .unavailable:
                unavailableState("No historical price data") {
                    Task {
                        await gameDetailViewModel.retryPrices(region: region)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 当前可购买的 Steam 激活价格 / Current prices for Steam-activating offers
    @ViewBuilder
    private func currentOffers(_ prices: ITADPriceGame) -> some View {
        let offers = (offerFilter == .steam ? prices.steamDeals : prices.deals)
            .sorted { $0.price.amountInt < $1.price.amountInt }

        if !offers.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text("CURRENT OFFERS")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(.brandGreen)

                }
                .padding(.top, 14)

                Picker("Offer filter", selection: $offerFilter) {
                    ForEach(OfferFilter.allCases, id: \.self) { filter in
                    let count = filter == .steam ? prices.steamDeals.count : prices.deals.count
                    Text("\(filter.rawValue) (\(count))")
                            .tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                .tint(.brandBlue)

                ForEach(Array(offers.enumerated()), id: \.offset) { index, offer in
                    currentOfferRow(
                        offer,
                        isBest: index == 0
                    )
                }
            }
        } else if offerFilter == .all {
            Text("No current offers")
                .font(.system(size: 14))
                .foregroundStyle(.secondaryText)
        }
    }

    /// 当前报价行，最低价用品牌色标记 / Current offer row; the cheapest offer gets the brand accent
    private func currentOfferRow(
        _ offer: ITADPriceDeal,
        isBest: Bool
    ) -> some View {
        let drmNames = offer.drm.map(\.name).joined(separator: ", ")

        return Button {
            guard let url = URL(string: offer.url) else { return }
            openURL(url)
        } label: {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 3, style: .continuous)
                    .fill(isBest ? Color.brandGreen : Color.secondaryText.opacity(0.5))
                    .frame(width: 5, height: 42)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 8) {
                        Text(offer.shop.name)
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                            .foregroundStyle(.primaryText)

                        if isBest {
                            Text("BEST")
                                .font(.system(size: 10, weight: .bold, design: .rounded))
                                .foregroundStyle(.appBackground)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(Color.brandGreen, in: Capsule())
                        }
                    }

                    Text(offer.cut > 0 ? "Save \(offer.cut)%" : "Current price")
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundStyle(offer.cut > 0 ? .brandBlue : .secondaryText)

                    Text(drmNames.isEmpty ? "DRM unavailable" : drmNames)
                        .font(.system(size: 11, weight: .semibold, design: .rounded))
                        .foregroundStyle(.secondaryText)
                        .lineLimit(1)
                }

                Spacer(minLength: 8)

                VStack(alignment: .trailing, spacing: 3) {
                    Text(offer.price.amount.formatted(.currency(code: offer.price.currency)))
                        .font(.system(size: 19, weight: .bold, design: .monospaced))
                        .foregroundStyle(isBest ? .brandGreen : .primaryText)

                    if offer.cut > 0 {
                        Text(
                            offer.regular.amount.formatted(.currency(code: offer.regular.currency))
                        )
                        .font(.system(size: 12, weight: .medium, design: .monospaced))
                        .foregroundStyle(.secondaryText)
                        .strikethrough()
                    }
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.vertical, 8)
        .overlay(alignment: .bottom) {
            Divider()
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "\(offer.shop.name), \(drmNames.isEmpty ? "DRM unavailable" : drmNames), "
                + "\(offer.price.amount.formatted(.currency(code: offer.price.currency)))"
        )
    }
    /// 一条历史价格 / One historical price row
    private func historicalPriceRow(label: String, price: ITADPrice, accent: Color) -> some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(accent)
                .frame(width: 5, height: 42)

            Text(label)
                .font(.system(size: 16, weight: .semibold, design: .rounded))
                .foregroundStyle(.primaryText)

            Spacer(minLength: 12)

            Text(price.amount.formatted(.currency(code: price.currency)))
                .font(.system(size: 20, weight: .bold, design: .monospaced))
                .foregroundStyle(accent)
        }
        .padding(.vertical, 6)
        .overlay(alignment: .bottom) {
            Divider()
        }
    }

    private func priceAccent(for index: Int) -> Color {
        switch index {
        case 0:
            return .brandGreen
        case 1:
            return .brandBlue
        default:
            return .secondaryText
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
