//
//  PreferencesSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import SwiftUI

/// Profile 偏好分组：区域、主题、语言三行；当前只读展示，接上设置选择器后再补交互 /
/// The preferences group: region, theme and language read-only for now; interaction follows with the pickers
struct PreferencesSectionView: View {

    /// 当前区域 / The current region
    let region: AppRegion

    /// 当前外观 / The current appearance
    let theme: AppTheme

    /// 当前语言 / The current language
    let language: AppLanguage

    var body: some View {
        VStack(spacing: 0) {
            row(
                icon: "globe.asia.australia.fill",
                label: "Region & Currency",
                value: "\(region.apiRegionCode) · \(region.currencyCode)",
                tint: .brandGreen
            )

            // 左端与行内文字对齐：14 行内边距 + 32 图标 + 12 间距 / Starts where the row's label does: 14 + 32 + 12
            Divider()
                .padding(.leading, 58)

            row(icon: "moon.fill", label: "Display Theme", value: theme.displayName, tint: .secondaryText)

            // 左端与行内文字对齐：14 行内边距 + 32 图标 + 12 间距 / Starts where the row's label does: 14 + 32 + 12
            Divider()
                .padding(.leading, 58)

            row(icon: "character.book.closed.fill", label: "Language", value: language.displayName, tint: .brandBlue)
        }
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    /// 一行偏好：图标方块、名称与等宽取值 /
    /// One preference row: icon tile, name, and a monospaced value so codes line up
    private func row(icon: String, label: String, value: String, tint: Color) -> some View {
        HStack(spacing: 12) {
            IconTileView(icon: icon, tint: tint)

            Text(label)
                .font(.system(size: 16, weight: .semibold, design: .rounded))
                .foregroundStyle(.primaryText)

            Spacer(minLength: 12)

            Text(value)
                .font(.system(size: 14, weight: .medium, design: .default))
                .foregroundStyle(.secondaryText)
                .lineLimit(1)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label), \(value)")
    }
}

#Preview {
    PreferencesSectionView(region: .unitedStates, theme: .dark, language: .english)
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.appBackground)
}
