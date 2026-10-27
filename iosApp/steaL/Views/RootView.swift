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

    /// 用户设置单例行，这里读显示主题与是否看过欢迎页 / Singleton user-settings row; the display theme and whether the welcome sheet was seen are read here
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    /// 全 App 共用的登录状态 / The sign-in state shared by the whole app
    @State private var authService = AuthService()

    /// 全 App 共用的 wishlist / The wishlist shared by the whole app
    @State private var wishlistService = WishlistService()

    /// App 是否在前台；回到前台时跑一次提醒检查。
    /// Whether the app is active; an alert check runs on each return to the foreground.
    @Environment(\.scenePhase) private var scenePhase

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
            // 每次回到前台检查一次降价与发售；离开前台时 task 被取消，下次回来再查。
            // Checks for price drops and releases on every return to the foreground; leaving cancels the task, and the next return checks again.
            .task(id: scenePhase) {
                await runAlertCheck()
            }
            // 首次启动盖在主界面上，关掉后主界面已经在下面。
            // Covers the main interface on first launch, which is already there underneath once it closes.
            .sheet(isPresented: isShowingOnboarding) {
                OnboardingSheet()
                    // 只能点 Continue 关，保证至少看一眼。
                    // Closes only through Continue, so it's seen at least once.
                    .interactiveDismissDisabled()
            }
    }

    // MARK: - 提醒检查 / Alert check

    /// 在前台、已登录、设置行已就绪时跑一次提醒检查。
    /// Runs one alert check when the app is active, signed in, and the settings row is ready.
    ///
    /// 暂时每次回到前台都查，方便在模拟器里验证；之后再加 12 小时间隔。
    /// For now it checks on every return so it's easy to verify in the simulator; the 12-hour interval comes later.
    private func runAlertCheck() async {
        guard scenePhase == .active else { return }
        guard let uid = authService.uid else { return }
        guard let currentSettings = settings.first else { return }

        await WishlistAlertChecker.run(
            uid: uid,
            region: currentSettings.region,
            notifyPriceDrops: currentSettings.isPriceDropAlertsEnabled,
            notifyReleases: currentSettings.isGameReleaseAlertsEnabled,
            wishlistService: wishlistService
        )
    }

    // MARK: - 欢迎页 / Onboarding

    /// 欢迎页是否显示，直接读写设置里的标记。
    /// Whether the welcome sheet shows, read from and written to the flag in settings.
    ///
    /// 不另存一份 `@State`：标记是唯一数据源，sheet 关闭时把它置为 `true`。
    /// No separate `@State`: the flag is the only source of truth, and closing the sheet sets it to `true`.
    private var isShowingOnboarding: Binding<Bool> {
        Binding(
            get: {
                // 设置行还没有时不弹。
                // Don't show it while the settings row is missing.
                let hasCompleted = settings.first?.hasCompletedOnboarding ?? true
                return !hasCompleted
            },
            set: { isPresented in
                if isPresented {
                    return
                }
                settings.first?.hasCompletedOnboarding = true
            }
        )
    }
}


// Option + Command + Return
#Preview(traits: .userSettingSampleData) {
    // 预览不走 `AppDelegate`，这里直接连真实 Firebase，否则 `AuthService()` / `WishlistService()` 调 Firebase 会崩；
    // 重复 `configure()` 也会崩，所以先判断 /
    // Previews skip `AppDelegate`, so connect to the real Firebase here or `AuthService()` / `WishlistService()` crash calling into Firebase;
    // a second `configure()` also crashes, hence the check
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()

    // 设置行由 `.userSettingSampleData` 插入，欢迎页的标记是 `false`，所以每次都会弹欢迎页。
    // The settings row comes from `.userSettingSampleData` with the onboarding flag `false`, so the welcome sheet always shows.
    RootView()
}
