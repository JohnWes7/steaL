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

    /// 是否弹出登录 sheet / Whether the sign-in sheet is shown
    @State private var isShowingSignIn = false

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.appBackground)
            .safeAreaBar(edge: .top, spacing: 0) {
                PageHeaderView(title: "Wishlist")
            }
            .toolbar(.hidden, for: .navigationBar)
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
            // 列表还没接 Firestore，先放空状态占位 / Not wired to Firestore yet, so show the empty state for now
            ContentUnavailableView(
                "No Games Yet",
                systemImage: "heart",
                description: Text("Games you add to your wishlist will appear here.")
            )
        }
    }
}

#Preview {
    // 预览不走 `AppDelegate`，直接连真实 Firebase / Previews skip `AppDelegate`, so connect to the real Firebase here
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()
    NavigationStack {
        WishlistView()
    }
    .environment(AuthService())
}
