//
//  GamesView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import SwiftUI

struct GamesView: View {
    /// 搜索关键字，过滤逻辑待接入 / Search keyword; filtering is not wired up yet
    @State private var searchText = ""
    /// 列表数据源，负责请求与状态 / Data source for the list; owns fetching and state
    @State private var gamesViewModel = GamesViewModel()

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
        .safeAreaInset(edge: .top, spacing: 0) {
            headerArea
        }
        .toolbar(.hidden, for: .navigationBar)
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
        case .loaded(let dealsData):
            // 请求成功但列表为空 / Request succeeded with an empty list
            if dealsData.list.isEmpty {
                emptyState
            } else {
                dealsContent(dealsData)
            }
        }
    }

    /// 首条 hero 卡 + 其余两列网格 / Hero card first, then a two-column grid
    @ViewBuilder
    private func dealsContent(_ dealsData: ITADDealsData) -> some View {

        // 首条 hero 卡 / Hero card for the first item
        if let firstDeal = dealsData.list.first {
            DealCardView(
                deal: firstDeal,
                isFavorite: gamesViewModel.isFavorite(firstDeal.id),
                onToggleFavorite: {
                    gamesViewModel.toggleFavorite(firstDeal.id)
                }
            )
        }

        // 其余条目排成两列网格 / Rest of the items in a two-column grid
        LazyVGrid(columns: gridColumns, spacing: 12) {
            ForEach(dealsData.list.dropFirst()) { deal in
                DealGridCardView(
                    deal: deal,
                    isFavorite: gamesViewModel.isFavorite(deal.id),
                    onToggleFavorite: {
                        gamesViewModel.toggleFavorite(deal.id)
                    }
                )
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

    /// 「Giveaways」区块标题，上方带一条分割线 / Section title for the Giveaways block, with a hairline divider above
    @ViewBuilder
    private var giveawayHeader: some View {
        VStack(alignment: .leading, spacing: 16) {
            // 分割线：把 Giveaways 区块与上面的 Hot Deals 隔开 / Divider cutting the Giveaways block away from Hot Deals
            // Rectangle()
            //     .fill(.white.opacity(0.08))
            //     .frame(height: 0.5)

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
        // 区块间留白：把分割线推离上一区块 / Gap above the divider, separating it from the previous block
        .padding(.top, 20)
        .padding(.bottom, 2)
    }


    
    /// 顶部固定区：品牌标题 + 搜索框 / Pinned top area: brand title and search field
    @ViewBuilder
    private var headerArea: some View {
        VStack(alignment: .leading, spacing: 8) {
            header
            searchField
        }
        .padding(.horizontal, 14)
        .padding(.top, 2)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.bar)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(.white.opacity(0.08))
                .frame(height: 0.5)
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
    
    /// 搜索输入框，回车提交；过滤逻辑待接入 / Search input field; return submits, filtering is not wired up yet
    @ViewBuilder
    private var searchField: some View {
        HStack(spacing: 14) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 19, weight: .medium))
                .foregroundStyle(.secondaryText)
            
            TextField("Search games, publishers...", text: $searchText)
                .foregroundStyle(.primaryText)
                .tint(.brandBlue)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
        }
        .padding(.horizontal, 20)
        .frame(height: 45)
        .background(Color.appBackground)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

#Preview {
    NavigationStack {
        GamesView()
    }
}
