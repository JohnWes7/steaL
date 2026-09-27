//
//  ProfileView.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import FirebaseCore
import OSLog
import SwiftData
import SwiftUI

struct ProfileView: View {

    private var profileViewModel = ProfileViewModel()

    @Environment(AuthService.self) private var authService
    @Environment(WishlistService.self) private var wishlistService

    /// 登出失败的文案，非 nil 时弹 alert / Sign-out failure message; shows the alert when non-nil
    @State private var errorMessage: String?
    /// 是否弹出登出确认 / Whether the sign-out confirmation is shown
    @State private var isConfirmingSignOut = false
    /// 是否弹出删号确认（带密码框）/ Whether the delete-account confirmation (with its password field) is shown
    @State private var isConfirmingDelete = false
    /// 删号确认里输入的密码 / The password typed into the delete-account confirmation
    @State private var deletePassword = ""
    /// 正在删号，行内显示转圈、挡住重复点击 / Deleting; the row shows a spinner and ignores repeat taps
    @State private var isDeletingAccount = false
    /// 删号失败的文案，非 nil 时弹 alert / Delete-account failure message; shows the alert when non-nil
    @State private var deleteErrorMessage: String?
    /// 是否弹出登录 sheet / Whether the sign-in sheet is shown
    @State private var isShowingSignIn = false

    /// 是否弹出欢迎页。
    /// Whether the welcome sheet is shown.
    ///
    /// 和首次启动是同一张 sheet，这里只是再看一遍，不改设置里的标记。
    /// The same sheet as on first launch; this is only for viewing it again and doesn't touch the flag in settings.
    @State private var isShowingWelcome = false

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

    /// AI Model 行右侧显示的模型名。
    /// Returns the model name shown on the AI Model row.
    ///
    /// - Parameter settings: 用户设置单例行 / The singleton settings row.
    /// - Returns: 选了自己的模型就是它的名字，否则是内置模型名 / The chosen model's name, or the built-in model's name when none is chosen.
    private func currentModelName(for settings: UserSettingModel) -> String {
        if let model = settings.llmModel {
            return model.name
        }
        return Config.llmModel
    }

    /// AI Model 行右侧模型名下面显示的服务商名。
    /// Returns the provider name shown under the model name on the AI Model row.
    ///
    /// 用户可能有好几个服务商，同名模型要靠它区分；内置写「Built-in」，和 AI Model 页的分组标题一致。
    /// A user may have several providers serving same-named models, so the provider tells them apart; the built-in one reads
    /// "Built-in", matching its group title on the AI Model page.
    ///
    /// - Parameter settings: 用户设置单例行 / The singleton settings row.
    /// - Returns: 选中模型所属服务商的名字，没选就是「Built-in」 / The chosen model's provider name, or "Built-in" when none is chosen.
    private func currentProviderName(for settings: UserSettingModel) -> String {
        if let model = settings.llmModel {
            return model.provider.name
        }
        return String(localized: "Built-in")
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

                Section("AI") {
                    // 推入一页而不是弹 sheet：里面有选模型和服务商列表，还要再弹编辑表单，是一层层的内容。
                    // A push rather than a sheet: the page holds the model choice and provider list and opens its own form.
                    NavigationLink {
                        AIModelSettingsView()
                    } label: {
                        LabeledContent {
                            // 模型名在上、服务商在下，同 Apple 设置里带副标题的值。
                            // Model on top, provider below, like a value with a subtitle in Settings.
                            VStack(alignment: .trailing, spacing: 2) {
                                Text(currentModelName(for: settings))

                                Text(currentProviderName(for: settings))
                                    .font(.footnote)
                            }
                        } label: {
                            HStack(spacing: 12) {
                                IconTileView(icon: "sparkles", tint: .brandBlue)

                                Text("AI Model")
                            }
                        }
                    }
                }

                Section("About") {
                    welcomeRow

                    // 标签 + 值，同 AI Model 行 / A label and its value, like the AI Model row
                    LabeledContent {
                        Text("\(appVersion) (Build \(buildNumber))")
                    } label: {
                        HStack(spacing: 12) {
                            IconTileView(icon: "info.circle.fill", tint: .secondaryText)

                            Text("App Version")
                        }
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

                    // 单独一个 Section，和 Sign Out 隔开，减少误触 / Its own section, set apart from Sign Out to avoid mis-taps
                    Section {
                        deleteAccountRow
                    } footer: {
                        Text("Permanently deletes your account and wishlist. This can't be undone.")
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
            // 再看一遍时可以下拉关，不加 `.interactiveDismissDisabled()`。
            // Swipe-to-dismiss is fine when viewing it again, so no `.interactiveDismissDisabled()`.
            .sheet(isPresented: $isShowingWelcome) {
                OnboardingSheet()
            }
            .alert(
                "Sign Out Failed",
                isPresented: Binding(
                    get: { errorMessage != nil },
                    set: { if !$0 { errorMessage = nil } }
                )
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    // MARK: - 欢迎页 / Welcome

    /// 再打开欢迎页的一行。
    /// The row that opens the welcome sheet again.
    ///
    /// HIG 要求引导看过之后还能在设置等地方找到。弹 sheet 而不是推入一页，所以不带 `chevron.right`（这个项目里它专指 App 内推页）。
    /// The HIG asks for onboarding to stay easy to find later, such as in settings. It presents a sheet rather than
    /// pushing a page, so it has no `chevron.right`, which in this project means an in-app push.
    private var welcomeRow: some View {
        Button {
            isShowingWelcome = true
        } label: {
            HStack(spacing: 12) {
                IconTileView(icon: "hand.wave.fill", tint: .brandGreen)

                // 按钮里当前色是 tint，写固定的 `Color.primary` 才是正文色。
                // Inside a button the current colour is the tint, so the fixed `Color.primary` keeps body text colour.
                Text("Welcome to steaL")
                    .foregroundStyle(Color.primary)
            }
            // 撑满整行、空白也可点，同 Sign Out 行。
            // Fills the row so the blank area is tappable too, like the Sign Out row.
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
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
            ProfileIdentityCardView(user: nil, trackingCount: 0) { isShowingSignIn = true }
        case .signedIn(let user):
            // Tracking 直接数环境里的 wishlist，Firestore 是唯一数据源 /
            // Tracking counts the wishlist from the environment; Firestore is the single source of truth
            let profileUser = ProfileUser(user: user)
            let trackingCount = wishlistService.items.count
            ProfileIdentityCardView(user: profileUser, trackingCount: trackingCount) { isShowingSignIn = true }
        }
    }

    // MARK: - 登出 / Sign out

    /// 成功后 `AuthService.state` 由监听器变成 `.signedOut` / On success the listener moves `AuthService.state` to `.signedOut`
    private func signOut() {
        do {
            try authService.signOut()
        } catch {
            Logger.auth.error("sign out failed / failed: \(String(describing: error))")
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - 删除账号 / Delete account

    /// 删号行：点了弹带密码框的确认 alert / The delete row; tapping shows the confirmation alert with a password field
    private var deleteAccountRow: some View {
        Button(role: .destructive) {
            isConfirmingDelete = true
        } label: {
            HStack(spacing: 12) {
                IconTileView(icon: "person.crop.circle.badge.xmark", tint: .red.opacity(0.75))

                Text("Delete Account")

                Spacer(minLength: 12)

                if isDeletingAccount {
                    ProgressView()
                }
            }
            // 撑满整行、空白也可点，和 Sign Out 行一样 / Fill the row and make the blank area tappable, like the Sign Out row
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .disabled(isDeletingAccount)
        // 删之前 Firebase 要求重新验证，所以确认和输密码放在同一个 alert 里，一步完成 /
        // Firebase needs a fresh sign-in before deleting, so confirming and entering the password happen in one alert
        .alert("Delete Account?", isPresented: $isConfirmingDelete) {
            SecureField("Password", text: $deletePassword)
                .textContentType(.password)
            Button("Cancel", role: .cancel) {
                deletePassword = ""
            }
            Button("Delete", role: .destructive, action: deleteAccount)
        } message: {
            Text("Your account and wishlist will be permanently deleted. Enter your password to confirm.")
        }
        .alert(
            "Couldn't Delete Account",
            isPresented: Binding(
                get: { deleteErrorMessage != nil },
                set: { isPresented in
                    if !isPresented {
                        deleteErrorMessage = nil
                    }
                }
            )
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(deleteErrorMessage ?? "")
        }
    }

    // 顺序：重新验证 → 删 wishlist → 删账号。先删数据：账号删了以后 Rules 不再放行，数据就成了删不掉的孤儿；
    // 反过来第三步失败时只是 wishlist 已空、账号还在，再删一次即可 /
    // Order: re-verify → delete wishlist → delete account. Data goes first because Rules reject it once the account is gone;
    // if the last step fails, the account just remains with an empty wishlist and the user can try again
    /// 成功后 `AuthService.state` 由监听器变成 `.signedOut`，这个 Section 随之隐藏 /
    /// On success the listener moves `AuthService.state` to `.signedOut` and this section hides
    private func deleteAccount() {
        let password = deletePassword
        deletePassword = ""
        guard !isDeletingAccount, let uid = authService.uid else { return }
        isDeletingAccount = true
        Task {
            defer { isDeletingAccount = false }
            do {
                try await authService.reauthenticate(password: password)
                try await wishlistService.deleteAll(uid: uid)
                try await authService.deleteAccount()
            } catch {
                Logger.auth.error("delete account failed / failed: \(String(describing: error))")
                deleteErrorMessage = error.localizedDescription
            }
        }
    }
}

// 没有 `main` 设置行页面会整页空白，由 `.userSettingSampleData` 插入 /
// Without the "main" settings row the page renders nothing, so `.userSettingSampleData` inserts one
#Preview(traits: .userSettingSampleData) {
    // 预览不走 `AppDelegate`，直接连真实 Firebase / Previews skip `AppDelegate`, so connect to the real Firebase here
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()

    // 复刻 `MainTabView`：`toolbar` 只在 `NavigationStack` 里生效 / Mirrors `MainTabView`; the modifier needs a `NavigationStack`
    NavigationStack {
        ProfileView()
    }
    .environment(AuthService())
    .environment(WishlistService())
}
