//
//  WishlistView.swift
//  steaL
//
//  Created by yijiang on 2026/9/22.
//

import SwiftUI
import FirebaseCore

struct WishlistView: View {

    @Environment(AuthService.self) private var authService
    @Environment(WishlistService.self) private var wishlistService

    /// 是否弹出登录 sheet / Whether the sign-in sheet is shown
    @State private var isShowingSignIn = false

    /// 点中的游戏，非 nil 就推详情页 / The tapped game; pushes the detail page once non-nil
    @State private var selectedRoute: GameDetailRoute?

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.appBackground)
            .safeAreaBar(edge: .top, spacing: 0) {
                PageHeaderView(title: "Wishlist")
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(item: $selectedRoute) { route in
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
            list
        }
    }

    /// 收藏列表：点行进详情，左滑或长按移除 / The wishlist: tap to open, swipe or long-press to remove
    private var list: some View {
        List {
            ForEach(wishlistService.items) { item in
                Button {
                    selectedRoute = GameDetailRoute(appID: item.appID, uuid: item.itadID, title: item.name)
                } label: {
                    WishlistRowView(item: item)
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                // 默认可以整行划到底直接移除，和「邮件」一致 / Full swipe removes by default, as in Mail
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        remove(item)
                    } label: {
                        Label("Remove", systemImage: "heart.slash")
                    }
                }
                .contextMenu {
                    Button("Remove from Wishlist", systemImage: "heart.slash", role: .destructive) {
                        remove(item)
                    }
                }
            }
        }
        .listStyle(.plain)
        // 露出下面的 appBackground / Let appBackground show through
        .scrollContentBackground(.hidden)
        // 数据由监听器推送，不在 withAnimation 里；长按移除和别处的增删也要有动画 /
        // The listener pushes changes outside withAnimation, so context-menu removals and outside edits still animate
        .animation(.default, value: wishlistService.items.map(\.appID))
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
