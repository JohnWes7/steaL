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
                .padding(20)
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
        // 来源页把导航栏藏起来了（`.toolbar(.hidden, for: .navigationBar)`），
        // 不在这里显式恢复的话详情页会连返回按钮一起没有 /
        // The source screen hides the navigation bar, so it has to be re-enabled here or the
        // detail page would also lose its back button
        .toolbar(.visible, for: .navigationBar)
        .navigationBarTitleDisplayMode(.inline)
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
            VStack(alignment: .leading, spacing: 18) {
                header(details)

                if let description = details.shortDescription {
                    Text(description)
                        .font(.system(size: 15))
                        .foregroundStyle(.secondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }

                infoSection

                priceSection

                parameterList

                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// ITAD 评分、厂商、标签与热度 / ITAD reviews, credits, tags and popularity
    @ViewBuilder
    private var infoSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("ITAD INFO")
                .font(.system(size: 13, weight: .bold))
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
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
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

    /// 标题区：优先用 Steam 的本地化标题，数据回来前用列表传过来的名字 /
    /// Title block; the localised Steam title once it is in, falling back to the list title
    private func header(_ details: SteamAppDetails) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("GAME DETAIL")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.brandGreen)

            Text(details.name ?? route.title)
                .font(.system(size: 30, weight: .heavy, design: .rounded))
                .foregroundStyle(.primaryText)
                .fixedSize(horizontal: false, vertical: true)

            if let date = details.releaseDate?.date {
                Text(details.isComingSoon ? "Coming soon · \(date)" : date)
                    .font(.system(size: 14, weight: .medium))
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
