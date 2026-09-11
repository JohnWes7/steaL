//
//  GamesView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import SwiftUI

struct GamesView: View {
    /// 搜索框绑定的关键字，目前仅作展示，本地过滤逻辑待接入 / Search keyword bound to the field; display-only until local filtering lands
    @State private var searchText = ""
    /// 列表数据的唯一数据源，负责请求、加载态与错误态 / Single source of truth for the list; owns fetching plus the loading and error state
    @State private var gamesViewModel = GamesViewModel()

    /// 两列等宽网格，间距与 `LazyVStack` 的 12 对齐 /
    /// Two equal-width grid columns; the spacing matches the `LazyVStack`'s 12
    private let gridColumns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
    ]
    
    /// 页面骨架：背景 + 状态机驱动的分支内容 + 顶部固定的 header /
    /// Screen skeleton: background, state-machine-driven content, and the top-pinned header
    var body: some View {
        ZStack {
            // 背景色 / Background color
            Color.appBackground
                .ignoresSafeArea()

            switch gamesViewModel.state {
            case .loading:
                loadingState
            case .failed(let error):
                errorState(error)
            case .loaded(let dealsData):
                // 状态机只保证「请求成功」，列表本身仍可能为空，所以这里再分一次 /
                // The state machine only guarantees a successful request; the list itself can still be
                // empty, hence this second branch
                if dealsData.list.isEmpty {
                    emptyState
                } else {
                    dealsList(dealsData)
                }
            }
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            headerArea
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: - 状态分支 / State branches

    /// 加载中，内容区居中 / Loading, centred in the content area
    @ViewBuilder
    private var loadingState: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// 加载失败。这里用 `ContentUnavailableView` 而不是裸 `Text`：一是和空态共用同一套排版，
    /// 二是它自带 actions 槽位，可以放重试按钮 —— 之前失败后没有任何出路，只能杀掉 App 重开 /
    /// Failure state. `ContentUnavailableView` instead of a bare `Text`: it matches the empty
    /// state's layout, and its actions slot carries a retry button — previously a failed load left
    /// no way out short of relaunching the app
    @ViewBuilder
    private func errorState(_ error: DealsError) -> some View {
        ContentUnavailableView {
            Label(error.title, systemImage: error.symbolName)
                .foregroundStyle(.secondaryText)
        } description: {
            Text(error.message)
                .foregroundStyle(.secondaryText)
        } actions: {
            retryButton(title: "Try Again")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// 列表为空。空列表不是失败，文案也不同，所以单独一份而不是复用错误态 /
    /// Empty list. An empty list is not a failure and needs different copy, so it gets its own
    /// builder instead of reusing the error state
    @ViewBuilder
    private var emptyState: some View {
        ContentUnavailableView {
            Label("No Deals Found", systemImage: "tag.slash")
                .foregroundStyle(.secondaryText)
        } description: {
            Text("There are no discounted games to show right now. Try again later.")
                .foregroundStyle(.secondaryText)
        } actions: {
            retryButton(title: "Refresh")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// 失败态与空态共用的重试按钮，改动只影响一处 /
    /// Retry button shared by the failure and empty states, so it only has to be tweaked in one place
    @ViewBuilder
    private func retryButton(title: String) -> some View {
        Button(title) {
            Task { await gamesViewModel.retry() }
        }
        .buttonStyle(.borderedProminent)
        .tint(.brandBlue)
    }

    // MARK: - 内容 / Content

    /// 有数据时的滚动内容：首条 hero 卡 + 其余两列网格 /
    /// Scrolling content when there is data: hero card for the first item, two-column grid for the rest
    ///
    /// 空列表已在 `emptyState` 处理，所以这里可以认为 `list` 非空 /
    /// Empty lists are handled by `emptyState` before this is reached, so the list is non-empty here
    @ViewBuilder
    private func dealsList(_ dealsData: ITADDealsData) -> some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                Section {
                    if let firstDeal = dealsData.list.first {
                        DealCardView(
                            deal: firstDeal,
                            isFavorite: gamesViewModel.isFavorite(firstDeal.id),
                            onToggleFavorite: {
                                gamesViewModel.toggleFavorite(firstDeal.id)
                            }
                        )
                    }

                    // 其余条目排成两列网格，卡片改用竖版 boxart /
                    // Everything else fills a two-column grid using portrait box art
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
                } header: {
                    hotDealsHeader
                }
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 16)
            .padding(.top, 20)
        }
    }

    /// 区块标题。注意 `wasSpotlight` 并不等于实时热门榜，它只代表「曾经进入过 Spotlight 且当前在打折」/
    /// Section title. `wasSpotlight` is not a live popularity chart; it only means "was in Spotlight and is discounted now"
    @ViewBuilder
    private var hotDealsHeader: some View {
        HStack(spacing: 10) {
            Text("🔥")
                .font(.system(size: 24))

            Text("Hot Deals")
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(.primaryText)

            Spacer(minLength: 0)
        }
        .padding(.bottom, 2)
    }


    
    /// 顶部固定区域，由品牌标题和搜索框组成；通过 `safeAreaInset` 贴顶并自动占据安全区高度 /
    /// Top pinned area holding the brand title and search field; attached with `safeAreaInset` so it reserves its own height
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
