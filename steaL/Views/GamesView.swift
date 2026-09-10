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
    
    /// 页面骨架：背景 + 状态分支内容 + 顶部固定的 header / Screen skeleton: background, state-driven content, and the top-pinned header
    var body: some View {
        ZStack {
            // 背景色 / Background color
            Color.appBackground
                .ignoresSafeArea()
            
            if gamesViewModel.isLoading {
                ProgressView()
                    .progressViewStyle(.circular)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)  // 全屏居中
            } else if let errorMessage = gamesViewModel.errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.secondaryText)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let dealsData = gamesViewModel.itadDealsData {
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
                            } else {
                                ContentUnavailableView(
                                    "No Deals Found",
                                    systemImage: "tag.slash",
                                    description: Text(
                                        "There are no discounted games to show right now. Try again later."
                                    )
                                )
                                .foregroundStyle(.secondaryText)
                                .frame(maxWidth: .infinity, alignment: .center)
                            }
                        } header: {
                            hotDealsHeader
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, 16)
                    .padding(.top, 20)
                }
            } else {
                // 空数据提示 / Empty state
                ContentUnavailableView(
                    "No Deals Found",
                    systemImage: "tag.slash",
                    description: Text("There are no discounted games to show right now. Try again later.")
                )
                .foregroundStyle(.secondaryText)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            headerArea
        }
        .toolbar(.hidden, for: .navigationBar)
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
