//
//  WishlistView.swift
//  steaL
//
//  Created by yijiang on 2026/9/22.
//

import SwiftUI
import SwiftData
import FirebaseCore

struct WishlistView: View {

    @Environment(AuthService.self) private var authService
    @Environment(WishlistService.self) private var wishlistService

    /// 用户设置单例行，只读区域 / Singleton user-settings row; read for the region
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    /// 每行的价格；收藏本身直接读 `wishlistService` / Each row's price; the items themselves come straight from `wishlistService`
    @State private var viewModel = WishlistViewModel()

    /// 是否弹出登录 sheet / Whether the sign-in sheet is shown
    @State private var isShowingSignIn = false

    /// 顶部选中的筛选项 / The selected filter
    @State private var filter: WishlistFilter = .all

    /// accessibility 字号时分段控件放不下，换成菜单 / At accessibility sizes the segmented control won't fit, so it becomes a menu
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    /// 当前区域，设置就绪前回退澳洲 / Current region; falls back to Australia before settings load
    private var region: AppRegion {
        settings.first?.region ?? .australia
    }

    /// 查价的触发条件：收藏增删或区域变化 / What triggers a price load: the wishlist changing or the region changing
    private var priceRequest: WishlistPriceRequest {
        let appIDs = wishlistService.items.map(\.appID)
        return WishlistPriceRequest(appIDs: appIDs, region: region)
    }

    /// 有收藏时才显示筛选 / The filter only shows once there is something to filter
    private var showsFilter: Bool {
        guard case .loaded = wishlistService.state else { return false }
        return !wishlistService.items.isEmpty
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.appBackground)
            .safeAreaBar(edge: .top, spacing: 0) {
                VStack(spacing: 0) {
                    PageHeaderView(title: "Wishlist")
                    if showsFilter {
                        filterPicker
                    }
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            // 挂在 List 外面：文档要求不要放进 List 这类懒加载容器 / Kept outside the List, as the docs say not to place it in lazy containers
            .navigationDestination(for: GameDetailRoute.self) { route in
                GameDetailView(route: route)
            }
            .sheet(isPresented: $isShowingSignIn) {
                SignInSheet()
            }
    }

    @ViewBuilder
    private var content: some View {
        switch authService.state {
        case .checking:
            // 只在启动瞬间出现，等 Firebase 第一次回调 / Only at launch, until Firebase's first callback
            ProgressView()
        case .signedOut:
            ContentUnavailableView {
                Label("Sign in to keep a wishlist", systemImage: "heart")
            } description: {
                Text("Save the games you're watching and see them on all your devices.")
            } actions: {
                Button("Sign In") { isShowingSignIn = true }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
            }
        case .signedIn:
            wishlist
        }
    }

    // MARK: - 已登录 / Signed in

    /// 按监听状态切换：等第一份快照、出错、空、列表 / Switches on the listener: first snapshot pending, failed, empty or the list
    @ViewBuilder
    private var wishlist: some View {
        switch wishlistService.state {
        case .idle, .loading:
            ProgressView()
        case .failed(let error):
            ContentUnavailableView {
                Label("Couldn't Load Wishlist", systemImage: "exclamationmark.icloud")
            } description: {
                Text(error.localizedDescription)
            }
        case .loaded where wishlistService.items.isEmpty:
            ContentUnavailableView(
                "No Games Yet",
                systemImage: "heart",
                description: Text("Tap the heart on any deal to save it here.")
            )
        case .loaded:
            gameList
        }
    }

    /// 收藏列表：点行进详情，左滑或长按移除 / The wishlist: tap to open, swipe or long-press to remove
    ///
    /// 筛选结果为空时 `List` 仍然留着，空状态盖在上面：查价的 `.task` 和下拉刷新都挂在它上面 /
    /// The `List` stays even when a filter is empty, with the empty state laid over it, since the price `.task` and pull to refresh live on it
    private var gameList: some View {
        let visibleItems = viewModel.items(wishlistService.items, matching: filter)
        let visibleAppIDs = visibleItems.map(\.appID)

        return List {
            ForEach(visibleItems) { item in
                // 收藏一定带 appID，点了必定能进，不需要首页那种先判断再跳的 Button /
                // Every saved item has an appID, so the row always navigates; no need for the home page's check-then-push Button
                NavigationLink(value: GameDetailRoute(appID: item.appID, uuid: item.itadID, title: item.name)) {
                    WishlistRowView(item: item, price: viewModel.priceState(for: item))
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                .contextMenu {
                    Button("Remove from Wishlist", systemImage: "trash", role: .destructive) {
                        remove(item)
                    }
                    .tint(.red)
                }
            }
            .onDelete { offsets in
                let removedItems = offsets.map { index in visibleItems[index] }
                for item in removedItems {
                    remove(item)
                }
            }
        }
        .listStyle(.plain)
        // 露出下面的 appBackground / Let appBackground show through
        .scrollContentBackground(.hidden)
        .overlay {
            if visibleItems.isEmpty {
                filterEmptyState
            }
        }
        // 数据由监听器推送，不在 withAnimation 里；长按移除、别处的增删和切换筛选都要有动画 /
        // The listener pushes changes outside withAnimation, so context-menu removals, outside edits and filter switches still animate
        .animation(.default, value: visibleAppIDs)
        // 切 tab 回来也会重跑，但 VM 只查还没有状态的行 / Reruns when returning to the tab, but the VM only fetches rows without a state
        .task(id: priceRequest) {
            let foundUUIDs = await viewModel.loadPrices(for: wishlistService.items, region: region)
            saveITADIDs(foundUUIDs)
        }
        // 和查价同一个触发条件；VM 只核对存的不是 false、一天内没核对过的行 /
        // Same trigger as prices; the VM only checks rows not stored as false and not checked within a day
        .task(id: priceRequest) {
            let statuses = await viewModel.checkReleases(for: wishlistService.items, region: region)
            saveReleaseStatuses(statuses)
        }
        // 系统下拉刷新，也是价格请求失败后的重试入口 / System pull to refresh; also how a failed price gets retried
        .refreshable {
            // 刷新中途写 `prices` 会重绘并取消 refreshable 自己的 Task；包一层独立 Task 不受影响（同 `GamesView`）/
            // Mid-refresh `prices` writes redraw and cancel refreshable's own task; a separate Task survives (as in `GamesView`)
            await Task { await refresh() }.value
        }
    }

    // MARK: - 筛选 / Filtering

    /// 顶部筛选：平时是分段控件，accessibility 字号时换成菜单 / The filter: segmented normally, a menu at accessibility sizes
    private var filterPicker: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                Picker("Filter", selection: $filter) {
                    ForEach(WishlistFilter.allCases) { option in
                        Text(option.fullName).tag(option)
                    }
                }
                .pickerStyle(.menu)
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Picker("Filter", selection: $filter) {
                    ForEach(WishlistFilter.allCases) { option in
                        Text(option.title)
                            .accessibilityLabel(option.fullName)
                            .tag(option)
                    }
                }
                .pickerStyle(.segmented)
            }
        }
        .padding(.horizontal, 14)
        .padding(.bottom, 8)
    }

    /// 筛选结果为空：这个筛选要的数据还在查就转圈，查完了才说没有 / An empty filter: a spinner while its data loads, the empty message only once it's in
    @ViewBuilder
    private var filterEmptyState: some View {
        if viewModel.hasPendingResults(in: wishlistService.items, for: filter) {
            ProgressView()
        } else {
            switch filter {
            case .all:
                // 有收藏才会显示列表，All 不会为空 / The list only shows when there are items, so All is never empty
                EmptyView()
            case .onSale:
                ContentUnavailableView(
                    "No Games on Sale",
                    systemImage: "tag",
                    description: Text("None of your saved games are discounted right now.")
                )
            case .allTimeLow:
                ContentUnavailableView(
                    "No All-Time Lows",
                    systemImage: "chart.line.downtrend.xyaxis",
                    description: Text("None of your saved games are at their lowest price ever.")
                )
            case .upcoming:
                ContentUnavailableView(
                    "No Upcoming Games",
                    systemImage: "calendar",
                    description: Text("All of your saved games are already out.")
                )
            }
        }
    }

    // MARK: - 刷新与回写 / Refreshing and writing back

    /// 下拉刷新：价格与发售状态并行重查 / Pull to refresh: prices and release status are refetched in parallel
    private func refresh() async {
        let items = wishlistService.items
        async let pricesRefreshed = viewModel.refresh(items: items, region: region)
        async let releasesRefreshed = viewModel.refreshReleases(for: items, region: region)
        let foundUUIDs = await pricesRefreshed
        let statuses = await releasesRefreshed
        saveITADIDs(foundUUIDs)
        saveReleaseStatuses(statuses)
    }

    /// 把 Steam 核对的发售状态连同核对时间写回 Firestore / Writes the release statuses checked with Steam back to Firestore, with the check time
    private func saveReleaseStatuses(_ statuses: [Int: Bool]) {
        guard let uid = authService.uid else { return }
        for (appID, isComingSoon) in statuses {
            wishlistService.updateReleaseStatus(isComingSoon: isComingSoon, appID: appID, uid: uid)
        }
    }

    /// 把 lookup 到的 ITAD UUID 写回 Firestore / Writes looked-up ITAD UUIDs back to Firestore
    private func saveITADIDs(_ uuids: [Int: String]) {
        guard let uid = authService.uid else { return }
        for (appID, uuid) in uuids {
            wishlistService.updateITADID(uuid, appID: appID, uid: uid)
        }
    }

    // MARK: - 移除 / Removing

    /// 同步写入本地队列，监听器立刻推送新列表 / Queues the delete locally; the listener pushes the new list at once
    private func remove(_ item: WishlistItem) {
        guard let uid = authService.uid else { return }
        wishlistService.remove(appID: item.appID, uid: uid)
    }
}

#Preview {
    // 预览不走 `AppDelegate`，直接连真实 Firebase / Previews skip `AppDelegate`, so connect to the real Firebase here
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()
    NavigationStack {
        WishlistView()
    }
    .environment(AuthService())
    .environment(WishlistService())
}
