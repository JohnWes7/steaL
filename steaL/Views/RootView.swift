//
//  RootView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import SwiftUI
import SwiftData
import FirebaseCore

struct RootView: View {

    /// 用户设置单例行，这里只读显示主题 / Singleton user-settings row; only the display theme is read here
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    /// 全 App 共用的登录状态 / The sign-in state shared by the whole app
    @State private var authService = AuthService()

    /// 全 App 共用的 wishlist / The wishlist shared by the whole app
    @State private var wishlistService = WishlistService()

    var body: some View {
        MainTabView()
            // nil 代表跟随系统 / nil means follow system
            .preferredColorScheme(settings.first?.theme.colorScheme)
            .environment(authService)
            .environment(wishlistService)
            // 挂在常驻的根视图上，切 tab 不会断开监听；换账号 / 登出时重启 /
            // On the always-present root so switching tabs keeps listening; restarts on account change or sign-out
            .task(id: authService.uid) {
                await wishlistService.listen(uid: authService.uid)
            }
    }
}


// Option + Command + Return
#Preview {
    // 预览不走 `AppDelegate`，这里直接连真实 Firebase，否则 `AuthService()` / `WishlistService()` 调 Firebase 会崩；
    // 重复 `configure()` 也会崩，所以先判断 /
    // Previews skip `AppDelegate`, so connect to the real Firebase here or `AuthService()` / `WishlistService()` crash calling into Firebase;
    // a second `configure()` also crashes, hence the check
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()
    let container = try! ModelContainer(
        for: UserSettingModel.self,
        configurations: ModelConfiguration(isStoredInMemoryOnly: true)
    )
    let _ = container.mainContext.insert(UserSettingModel())
    
    // 复刻 `MainTabView`：`toolbar` 只在 `NavigationStack` 里生效 / Mirrors `MainTabView`; the modifier needs a `NavigationStack`
    RootView()
        .modelContainer(container)
}
