//
//  ProfileView.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import SwiftData
import SwiftUI

struct ProfileView: View {

    private var profileViewModel = ProfileViewModel()

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
                ProfileIdentityCardView(user: .mock)
                    .listRowInsets(EdgeInsets())        // 去掉 List 给行的边距
                    .listRowBackground(Color.clear)     // 去掉行底色，只留卡片自己的
                    .listRowSeparator(.hidden)          // 顶层行还会画一条分隔线，一起关掉

                Section("Preferences") {
                    HStack(spacing: 12) {
                        IconTileView(icon: "globe.asia.australia.fill", tint: .brandGreen)

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

                Section {
                    HStack(spacing: 12) {
                        IconTileView(icon: "rectangle.portrait.and.arrow.right", tint: .red.opacity(0.75))

                        Text("Sign Out")
                            .foregroundStyle(.red)
                    }
                } footer: {
                    Text("steaL · FIT3178 Project")
                        .font(.system(size: 12, weight: .medium, design: .default))
                        .foregroundStyle(.secondaryText)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.top, 16)
                }


            }
            .listStyle(.insetGrouped)
            .tint(.brandGreen)
            .scrollContentBackground(.hidden)
            .background(Color.appBackground)
            // 顶栏与主页同款：玻璃背景 + 内边距写在调用点 / Same top bar as home; the blur comes from here, padding at the call site
            .safeAreaBar(edge: .top, spacing: 0) {
                ProfileHeaderView()
                    .padding(.horizontal, 14)
                    .padding(.top, 2)
                    .padding(.bottom, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.bar)
            }
            .toolbar(.hidden, for: .navigationBar)
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

    // 复刻 `MainTabView`：`toolbar` 只在 `NavigationStack` 里生效 / Mirrors `MainTabView`; the modifier needs a `NavigationStack`
    NavigationStack {
        ProfileView()
    }
    .modelContainer(container)
}
