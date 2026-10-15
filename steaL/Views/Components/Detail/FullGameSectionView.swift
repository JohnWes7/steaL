//
//  FullGameSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 完整游戏入口：试玩版页面上用它推入本体详情页；与 `DemoSectionView` 刻意相反（蓝 = 留在 App）/
/// The full game entry: pushes the base game's page; the opposite of the demo entry, whose
/// green slab leaves the app while blue only accents a push that stays inside
struct FullGameSectionView: View {

    /// 完整游戏的名称 / The full game's name
    let name: String

    /// 打开完整游戏，导航由页面负责 / Opens the full game; the page owns the navigation
    let open: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("FULL GAME")
                .font(.system(.footnote, weight: .bold))
                .foregroundStyle(.brandGreen)

            Button(action: open) {
                HStack(spacing: 12) {
                    Image(systemName: "gamecontroller.fill")
                        .font(.system(.subheadline, weight: .bold))
                        .foregroundStyle(.appBackground)
                        .frame(width: 32, height: 32)
                        .background(
                            Color.brandBlue,
                            in: RoundedRectangle(cornerRadius: 9, style: .continuous)
                        )

                    VStack(alignment: .leading, spacing: 3) {
                        Text(name)
                            .font(.system(.callout, design: .rounded, weight: .semibold))
                            .foregroundStyle(.primaryText)

                        Text("Opens in this app")
                            .font(.system(.footnote, design: .rounded, weight: .medium))
                            .foregroundStyle(.secondaryText)
                    }

                    Spacer(minLength: 12)

                    // 蓝色只做小面积强调：`surfaceElevated` 上的蓝色正文只有 4.13:1，不达标 / Blue body copy only reaches 4.13:1
                    Image(systemName: "chevron.right")
                        .font(.system(.callout, weight: .bold))
                        .foregroundStyle(.brandBlue)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    Color.surfaceElevated,
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                )
                .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .combine)
            // label 只说是什么，点了会怎样写在 hint / The label says what it is; the hint says what happens
            .accessibilityLabel(name)
            .accessibilityHint("Shows the full game's details")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview("完整游戏入口 / Full game entry") {
    FullGameSectionView(
        name: "Probably Stolen - Cyberpunk Shopkeeper Sim",
        open: {}
    )
    .padding(20)
    .background(Color.appBackground)
}
