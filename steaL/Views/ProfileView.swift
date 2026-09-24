//
//  ProfileView.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import FirebaseCore
import SwiftData
import SwiftUI

struct ProfileView: View {

    private var profileViewModel = ProfileViewModel()

    @Environment(AuthService.self) private var authService

    /// 登出失败的文案，非 nil 时弹 alert / Sign-out failure message; shows the alert when non-nil
    @State private var errorMassage: String?
    /// 是否弹出登出确认 / Whether the sign-out confirmation is shown
    @State private var isConfirmingSignOut = false
    /// 是否弹出登录 sheet / Whether the sign-in sheet is shown
    @State private var isShowingSignIn = false

    /// 用户设置单例行，偏好分组读它的真实值 / Singleton user-settings row; the preferences group reads its real values
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    /// 版本号与构建号来自 Info.plist，跟着 Xcode 的 Version / Build 走 /
    /// Version and build number come from the bundle, so they follow Xcode's Version / Build fields
    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
    }

    /// `CFBundleVersion` / The bundle's build number
    private var buildNumber: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "—"
    }

    var body: some View {
        // `@Bindable` 需要非可选对象 / `@Bindable` needs a non-optional object
        if let storedSettings = settings.first {
            @Bindable var settings = storedSettings

            List {
                // same as apple setting
                Section {
                    identityCard
                }

                Section("Preferences") {
                    HStack(spacing: 12) {
                        IconTileView(icon: "globe.asia.australia.fill", tint: .brandGreen) // 和label的区别

                        Picker("Region & Currency", selection: $settings.region) {
                            ForEach(AppRegion.allCases) { option in
                                Text(option.displayName).tag(option)
                            }
                        }
                    }

                    HStack(spacing: 12) {
                        IconTileView(icon: "moon.fill", tint: .secondaryText)

                        Picker("Display Theme", selection: $settings.theme) {
                            ForEach(AppTheme.allCases) { option in
                                Text(option.displayName).tag(option)
                            }
                        }
                    }

                    HStack(spacing: 12) {
                        IconTileView(icon: "character.book.closed.fill", tint: .brandBlue)

                        Picker("Language", selection: $settings.language) {
                            ForEach(AppLanguage.allCases) { option in
                                Text(option.displayName).tag(option)
                            }
                        }
                    }
                }

                Section("Notifications") {
                    Toggle(isOn: $settings.isPriceDropAlertsEnabled) {
                        HStack(spacing: 12) {
                            IconTileView(icon: "bell.fill", tint: .brandBlue)

                            Text("Price Drop Alerts")
                        }
                    }

                    Toggle(isOn: $settings.isGameReleaseAlertsEnabled) {
                        HStack(spacing: 12) {
                            IconTileView(icon: "calendar.badge.clock", tint: .secondaryText)

                            Text("Game Release Alerts")
                        }
                    }
                }

                Section("About") {
                    HStack(spacing: 12) {
                        IconTileView(icon: "info.circle.fill", tint: .secondaryText)

                        Text("App Version")

                        Spacer(minLength: 12)

                        Text("\(appVersion) (Build \(buildNumber))")
                            .foregroundStyle(.secondaryText)
                    }

                    // 右侧箭头表示以后会推入一页 / The chevron marks a row that will push a page later
                    HStack(spacing: 12) {
                        IconTileView(icon: "person.text.rectangle.fill", tint: .secondaryText)

                        Text("About Me")

                        Spacer(minLength: 12)

                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.tertiary)
                    }
                }

                // 只有登录后才有东西可登出
                // Only signed-in users have anything to sign out of; hide the whole section so no empty gap is left
                if case .signedIn = authService.state {
                    Section {
                        // `.destructive` 让文字自动变红 / `.destructive` turns the title red on its own
                        Button(role: .destructive) {
                            isConfirmingSignOut = true
                        } label: {
                            HStack(spacing: 12) {
                                IconTileView(icon: "rectangle.portrait.and.arrow.right", tint: .red.opacity(0.75))

                                Text("Sign Out")
                            }
                            // 撑满整行、空白也可点，和其他行一样整行响应 / Fill the row and make the blank area tappable, like the other rows
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        // 先确认再登出，点下去时这一行还在，能看到按下反馈 / Confirm first, so the row stays long enough to show its highlight
                        .confirmationDialog("Sign out of steaL?", isPresented: $isConfirmingSignOut, titleVisibility: .visible) {
                            Button("Sign Out", role: .destructive, action: signOut)
                        }
                    }
                }

                // 页脚单独一个空 Section：Sign Out 隐藏时，它不会跟着空 Section 离上面很远 /
                // Footer lives in its own empty section, so hiding Sign Out doesn't leave it floating far below
                Section {
                } footer: {
                    Text("steaL · FIT3178 Project")
                        .font(.system(size: 12, weight: .medium, design: .default))
                        .foregroundStyle(.secondaryText)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
                .listSectionSpacing(12)


            }
            // is default
            .listStyle(.insetGrouped)
            .tint(.brandGreen)
            // 隐藏 List 自带的系统灰底，才能露出下面的 appBackground / Hide List's default grouped background so appBackground shows through
            .scrollContentBackground(.hidden)
            .background(Color.appBackground)
            .safeAreaBar(edge: .top, spacing: 0) {
                PageHeaderView(title: "Profile")
            }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $isShowingSignIn) {
                SignInSheet()
            }
            .alert(
                "Sign Out Failed",
                isPresented: Binding(
                    get: { errorMassage != nil },
                    set: { if !$0 { errorMassage = nil } }
                )
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorMassage ?? "")
            }
        }
    }

    // MARK: - 身份卡 / Identity card

    /// 按登录状态切换身份卡 / The identity card for the current sign-in state
    @ViewBuilder
    private var identityCard: some View {
        switch authService.state {
        case .checking:
            // 只在启动瞬间出现；不先显示 Guest 卡，免得已登录用户闪一下 /
            // Only at launch; not the Guest card, so signed-in users don't see it flash
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
        case .signedOut:
            ProfileIdentityCardView(user: nil) { isShowingSignIn = true }
        case .signedIn(let user):
            ProfileIdentityCardView(user: ProfileUser(user: user)) { isShowingSignIn = true }
        }
    }

    // MARK: - 登出 / Sign out

    /// 成功后 `AuthService.state` 由监听器变成 `.signedOut` / On success the listener moves `AuthService.state` to `.signedOut`
    private func signOut() {
        do {
            try authService.signOut()
        } catch {
            errorMassage = error.localizedDescription
        }
    }
}

#Preview {
    // 便利版容器是空的，`settings.first` 取不到 `main` 行页面就整页空白，先插一条默认设置 /
    // The convenience container is empty, and without the "main" row `settings.first` is nil so the page renders nothing
    let container = try! ModelContainer(
        for: UserSettingModel.self,
        configurations: ModelConfiguration(isStoredInMemoryOnly: true)
    )
    let _ = container.mainContext.insert(UserSettingModel())
    // 预览不走 `AppDelegate`，直接连真实 Firebase / Previews skip `AppDelegate`, so connect to the real Firebase here
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()

    // 复刻 `MainTabView`：`toolbar` 只在 `NavigationStack` 里生效 / Mirrors `MainTabView`; the modifier needs a `NavigationStack`
    NavigationStack {
        ProfileView()
    }
    .modelContainer(container)
    .environment(AuthService())
}
