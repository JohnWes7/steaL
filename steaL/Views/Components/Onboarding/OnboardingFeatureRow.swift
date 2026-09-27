//
//  OnboardingFeatureRow.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import SwiftUI

/// 欢迎页里的一行功能介绍：左边图标，右边标题和一句说明。
/// One feature row on the welcome sheet: an icon on the left, a title and one line of description on the right.
///
/// 写法仿 Apple 自家 App 首次打开时的 Welcome 页，每行只讲一件事。
/// Modelled on the welcome screens of Apple's own apps, where each row covers one thing.
struct OnboardingFeatureRow: View {

    /// 左边的 SF Symbol 名字。
    /// The SF Symbol name shown on the left.
    let systemImage: String

    /// 功能名。
    /// The feature's name.
    let title: LocalizedStringKey

    /// 一句话说明怎么用。
    /// One sentence on how to use it.
    let description: LocalizedStringKey

    /// 图标列的宽度，随 Dynamic Type 放大，保证几行的文字左边对齐。
    /// The icon column's width; scales with Dynamic Type so every row's text lines up.
    @ScaledMetric(relativeTo: .title) private var iconWidth: CGFloat = 44

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: systemImage)
                .font(.title)
                .foregroundStyle(.brandGreen)
                .frame(width: iconWidth)
                // 图标只是装饰，VoiceOver 读标题和说明就够了。
                // The icon is decorative; VoiceOver reads the title and description.
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(.headline, design: .rounded))
                    .foregroundStyle(.primaryText)

                Text(description)
                    .font(.system(.subheadline, design: .rounded))
                    .foregroundStyle(.secondaryText)
            }
            // 允许换行，大字号时说明不被截断。
            // Lets the text wrap so the description isn't truncated at large sizes.
            .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    OnboardingFeatureRow(
        systemImage: "tag.fill",
        title: "Find Deals",
        description: "Browse today's best Steam deals and free giveaways."
    )
    .padding()
}
