//
//  PrivacyPolicyView.swift
//  steaL
//
//  Created by yijiang on 2026/10/15.
//

import SwiftUI

/// 隐私政策全文。
/// The full privacy policy.
///
/// 按 App Review Guidelines 5.1.1(i) 要写清楚：收集什么、怎么收集、发给谁、保留多久、怎么删除。
/// 内容对应代码现状，改了数据流（新字段、新服务）要一起改这里和日期。
/// Covers what App Review Guidelines 5.1.1(i) asks for: what is collected, how, who it is shared with, how long it is
/// kept and how to delete it. The text mirrors the current code, so a change to the data flow (a new field or service)
/// must update this page and its date too.
struct PrivacyPolicyView: View {

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                intro

                PolicySection(title: "You Don't Need an Account") {
                    Text("Browsing deals, searching, viewing game details and asking for AI advice all work without signing in. You only need an account to keep a wishlist.")
                }

                PolicySection(title: "Stored on This Device") {
                    PolicyBullet("**Settings**: region, language, appearance, notification toggles and your AI model choice. They are never uploaded.")
                    PolicyBullet("**API keys** you add for your own AI provider are kept in the iOS Keychain and only sent to that provider.")
                    PolicyBullet("**Images** are cached so pages load faster.")
                }

                PolicySection(title: "Your Account and Wishlist") {
                    Text("Accounts are handled by Firebase Authentication, a Google service. It stores your email address, your name and a secure form of your password; steaL never stores your password.")

                    Text("Your wishlist is saved in Cloud Firestore under your account. For each game it keeps the Steam app ID, name, cover image link, IsThereAnyDeal ID, release status, the lowest price seen in your region and when you added it. Only your signed-in account can read or change it.")
                }

                PolicySection(title: "Sent to Other Services") {
                    Text("To show prices and game details, steaL sends requests to:")

                    PolicyBullet("**IsThereAnyDeal**: game IDs and your selected region.")
                    PolicyBullet("**Steam Store**: what you type in Search, game IDs, your region and language.")
                    PolicyBullet("**AI provider**, only when you ask for AI advice: the game's name, type, release date, genres, tags, prices, reviews and player counts. This goes to the built-in service (DeepSeek) or to the provider you add in Profile › AI Model.")

                    Text("These requests never include your email, name or wishlist. Like any internet request, the service can see your IP address. Each service handles requests under its own privacy policy.")
                }

                PolicySection(title: "Notifications") {
                    Text("If you turn on Price Drop Alerts or Game Release Alerts, steaL checks your wishlist each time you open the app and posts notifications from this device. Nothing is sent through a notification server. You can turn them off in Profile or in iOS Settings at any time.")
                }

                PolicySection(title: "What steaL Doesn't Do") {
                    PolicyBullet("No advertising.")
                    PolicyBullet("No analytics or crash reporting services.")
                    PolicyBullet("No tracking across other apps or websites.")
                    PolicyBullet("Your data is never sold or shared for advertising.")
                }

                PolicySection(title: "Keeping and Deleting Your Data") {
                    Text("Your wishlist is kept until you remove games or delete your account. **Profile › Delete Account** permanently deletes your wishlist and then your account straight away; this can't be undone.")

                    Text("You can remove an AI provider and its key in Profile › AI Model. Settings and cached images are removed when you delete the app.")
                }

                PolicySection(title: "Contact") {
                    Text("Questions about this policy or your data? Email [yluo0117@student.monash.edu](mailto:yluo0117@student.monash.edu).")
                }

                PolicySection(title: "Changes") {
                    Text("If this policy changes, the date at the top of this page will be updated.")
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
        .background(Color.appBackground)
        .navigationTitle("Privacy Policy")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - 开头 / Intro

    /// 更新日期和一段总述。
    /// The last-updated date and a short summary.
    private var intro: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Last updated 16 October 2026")
                .font(.footnote)
                .foregroundStyle(.secondaryText)

            Text("steaL is a student project made for Monash University's FIT3178 unit. This policy explains what the app stores, where it is sent and how you can delete it.")
                .foregroundStyle(.primaryText)
        }
    }
}

// MARK: - 段落 / Sections

/// 一段带标题的条款。
/// One titled section of the policy.
private struct PolicySection<Content: View>: View {

    /// 段落标题 / The section title.
    let title: String

    /// 段落正文 / The section body.
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
                .foregroundStyle(.primaryText)
                .accessibilityAddTraits(.isHeader)

            content
                .foregroundStyle(.primaryText)
        }
    }
}

/// 带圆点的一条列表项，换行后和第一行文字对齐。
/// A bulleted list item whose wrapped lines align with its first line.
private struct PolicyBullet: View {

    /// 列表项正文，支持 Markdown 加粗 / The item text, with Markdown bold.
    let text: LocalizedStringKey

    /// 建一条列表项。
    /// Creates a list item.
    ///
    /// - Parameter text: 列表项正文 / The item text.
    init(_ text: LocalizedStringKey) {
        self.text = text
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(verbatim: "•")
                .foregroundStyle(.secondaryText)
                .accessibilityHidden(true)

            Text(text)
        }
    }
}

#Preview {
    NavigationStack {
        PrivacyPolicyView()
    }
}
