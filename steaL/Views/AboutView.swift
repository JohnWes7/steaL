//
//  AboutView.swift
//  steaL
//
//  Created by yijiang on 2026/10/15.
//

import SwiftUI

/// 关于页：数据来源、隐私政策、第三方库、开发者与商标声明。
/// The About page: data sources, privacy policy, third-party libraries, developer and trademark notices.
///
/// 全是静态内容，不持有状态。ITAD 条款要求注明数据来自 IsThereAnyDeal、且不能让人以为和它有关联；
/// Steam 条款同样不许暗示与 Valve 有关联，所以两条声明都写在来源分组的页脚。
/// Everything here is static and the page holds no state. ITAD's terms ask apps to credit IsThereAnyDeal without
/// implying any affiliation, and Steam's terms forbid implying a relationship with Valve, so both notices sit in the
/// data sources footer.
struct AboutView: View {

    var body: some View {
        List {
            Section {
                header
            }
            .listRowBackground(Color.clear)

            dataSourcesSection

            Section {
                // 推入一页读长文，同 Profile 里 AI Model 的写法。
                // Pushes a page for the long text, like Profile's AI Model row.
                NavigationLink {
                    PrivacyPolicyView()
                } label: {
                    HStack(spacing: 12) {
                        IconTileView(icon: "hand.raised.fill", tint: .brandBlue)

                        Text("Privacy Policy")
                    }
                }
            } header: {
                Text("Privacy")
            } footer: {
                Text("No ads, no analytics and no tracking. Your settings stay on this device.")
            }

            Section {
                AboutLinkRow(
                    title: "Firebase iOS SDK",
                    subtitle: "Apache License 2.0",
                    icon: "shippingbox.fill",
                    tint: .secondaryText,
                    urlString: "https://github.com/firebase/firebase-ios-sdk"
                )
            } header: {
                Text("Acknowledgements")
            } footer: {
                Text("Used for signing in and saving your wishlist.")
            }

            developerSection
        }
        .tint(.brandGreen)
        // 和 Profile 一样露出 appBackground。
        // Shows appBackground, like Profile.
        .scrollContentBackground(.hidden)
        .background(Color.appBackground)
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - 头部 / Header

    /// App 图标、名字与一句介绍。
    /// The app icon, name and a one-line description.
    private var header: some View {
        VStack(spacing: 12) {
            // App Icon 的静态导出，圆角已在图里。
            // Static export of the app icon, with the corners baked in.
            Image(.appIcon)
                .resizable()
                .frame(width: 80, height: 80)
                .accessibilityHidden(true)

            Text("steaL")
                .font(.system(.title2, design: .rounded, weight: .bold))
                .foregroundStyle(.primaryText)

            Text("Find the best price before you buy a Steam game.")
                .font(.system(.subheadline, design: .rounded))
                .foregroundStyle(.secondaryText)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    // MARK: - 数据来源 / Data sources

    /// 价格与游戏信息从哪里来，以及 AI 建议的免责说明。
    /// Where prices and game details come from, plus the AI advice disclaimer.
    private var dataSourcesSection: some View {
        Section {
            AboutLinkRow(
                title: "IsThereAnyDeal",
                subtitle: "Prices, price history, deals and giveaways",
                icon: "tag.fill",
                tint: .brandBlue,
                urlString: "https://isthereanydeal.com"
            )

            AboutLinkRow(
                title: "Steam Store",
                subtitle: "Game details, screenshots, trailers and search",
                icon: "gamecontroller.fill",
                tint: .brandGreen,
                urlString: "https://store.steampowered.com"
            )

            // AI 不是网站，没有链接；说明写在副标题里。
            // The AI isn't a website, so the row has no link; the note goes in the subtitle.
            HStack(spacing: 12) {
                IconTileView(icon: "sparkles", tint: .brandBlue)

                VStack(alignment: .leading, spacing: 2) {
                    Text("AI Advice")

                    Text("Written by a language model and can be wrong. Check the price before you buy.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .accessibilityElement(children: .combine)
        } header: {
            Text("Data Sources")
        } footer: {
            Text("Price data provided by the IsThereAnyDeal API. steaL is an independent project and is not affiliated with or endorsed by IsThereAnyDeal, Valve or Steam. Prices are shown as provided and can change at any time.")
        }
    }

    // MARK: - 开发者 / Developer

    /// 谁做的、为什么做，以及商标声明。
    /// Who made the app and why, plus the trademark notice.
    private var developerSection: some View {
        Section {
            LabeledContent {
                Text("Yijiang Luo")
            } label: {
                HStack(spacing: 12) {
                    IconTileView(icon: "person.fill", tint: .secondaryText)

                    Text("Developer")
                }
            }

            LabeledContent {
                Text("Monash FIT3178")
            } label: {
                HStack(spacing: 12) {
                    IconTileView(icon: "graduationcap.fill", tint: .secondaryText)

                    Text("Course")
                }
            }
        } header: {
            Text("Developer")
        } footer: {
            Text("Steam and the Steam logo are trademarks of Valve Corporation. Game names, images and trailers belong to their respective owners.")
        }
    }
}

#Preview {
    NavigationStack {
        AboutView()
    }
}
