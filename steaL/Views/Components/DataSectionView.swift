//
//  DataSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/17.
//

import SwiftUI

/// Profile 数据分组：App 版本与 About Me；About Me 的跳转尚未接入 /
/// The data group: the app version and About Me; the About Me push is not wired yet
struct DataSectionView: View {

    /// 版本号 / The app's short version string
    let version: String

    /// 构建号 / The app's build number
    let build: String

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                IconTileView(icon: "info.circle.fill", tint: .secondaryText)

                Text("App Version")
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundStyle(.primaryText)

                Spacer(minLength: 12)

                Text("\(version) (Build \(build))")
                    .font(.system(size: 14, weight: .medium, design: .default))
                    .foregroundStyle(.secondaryText)
                    .lineLimit(1)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .accessibilityElement(children: .combine)
            .accessibilityLabel("App Version, \(version), build \(build)")

            // 左端与行内文字对齐：14 行内边距 + 32 图标 + 12 间距 / Starts where the row's label does: 14 + 32 + 12
            Divider()
                .padding(.leading, 58)

            // 右侧箭头表示以后会推入一页，和其他分组的取值行区分开 /
            // The chevron marks a row that will push a page, telling it apart from the value rows
            HStack(spacing: 12) {
                IconTileView(icon: "person.text.rectangle.fill", tint: .secondaryText)

                Text("About Me")
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundStyle(.primaryText)

                Spacer(minLength: 12)

                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.secondaryText)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .accessibilityElement(children: .combine)
            .accessibilityLabel("About Me")
        }
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

#Preview {
    DataSectionView(version: "1.0", build: "1")
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.appBackground)
}
