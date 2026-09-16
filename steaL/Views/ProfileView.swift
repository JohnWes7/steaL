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

    /// 设置就绪前回退默认值 / Falls back to the defaults before settings load
    private var region: AppRegion {
        settings.first?.region ?? .australia
    }

    /// 设置就绪前回退默认值 / Falls back to the defaults before settings load
    private var theme: AppTheme {
        settings.first?.theme ?? .system
    }

    /// 设置就绪前回退默认值 / Falls back to the defaults before settings load
    private var language: AppLanguage {
        settings.first?.language ?? .english
    }

    /// 设置就绪前回退默认值 / Falls back to the defaults before settings load
    private var isPriceDropAlertsEnabled: Bool {
        settings.first?.isPriceDropAlertsEnabled ?? true
    }

    /// 设置就绪前回退默认值 / Falls back to the defaults before settings load
    private var isReleaseAlertsEnabled: Bool {
        settings.first?.isGameReleaseAlertsEnabled ?? true
    }

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
        ZStack {
            // 背景色 / Background color
            Color.appBackground
                .ignoresSafeArea()

            ScrollView {
                // 分组只靠卡片间距区分，不加 section 标题，跟系统设置一致 /
                // Groups are told apart by spacing alone, with no section titles, as in Settings
                VStack(alignment: .leading, spacing: 24) {
                    ProfileIdentityCardView(user: .mock)

                    PreferencesSectionView(region: region, theme: theme, language: language)

                    NotificationsSectionView(
                        isPriceDropAlertsEnabled: isPriceDropAlertsEnabled,
                        isReleaseAlertsEnabled: isReleaseAlertsEnabled
                    )

                    DataSectionView(version: appVersion, build: buildNumber)

                    SignOutSectionView()

                    Text("steaL · FIT3178 Project")
                        .font(.system(size: 12, weight: .medium, design: .monospaced))
                        .foregroundStyle(.secondaryText)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.top, 12)
                }
                .padding(.horizontal, 14)
                .padding(.top, 20)
                .padding(.bottom, 16)
            }
        }
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

#Preview {
    // 复刻 `MainTabView`：`toolbar` 只在 `NavigationStack` 里生效 / Mirrors `MainTabView`; the modifier needs a `NavigationStack`
    NavigationStack {
        ProfileView()
    }
    .modelContainer(for: UserSettingModel.self, inMemory: true)
}
