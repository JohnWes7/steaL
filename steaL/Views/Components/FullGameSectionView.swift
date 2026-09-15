//
//  FullGameSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 完整游戏入口：试玩版页面上用它推入本体详情页 /
/// The full game entry: pushes the base game's detail page from a demo's page
///
/// 它与 `DemoSectionView` 是同一对的两个方向，形状与颜色刻意做得不同 —— 绿色实心块是离开 App
/// 去 Steam 拿试玩版，这里的蓝色只做强调，表示点一下留在 App 里翻页 /
/// It is the other half of the pair with `DemoSectionView`, deliberately shaped and coloured
/// differently: the green slab leaves the app for Steam, while blue only accents a push that
/// stays inside the app
struct FullGameSectionView: View {

    /// 完整游戏的名称 / The full game's name
    let name: String

    /// 打开完整游戏，导航由页面负责 / Opens the full game; the page owns the navigation
    let open: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("FULL GAME")
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(.brandGreen)

            Button(action: open) {
                HStack(spacing: 12) {
                    Image(systemName: "gamecontroller.fill")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.appBackground)
                        .frame(width: 32, height: 32)
                        .background(
                            Color.brandBlue,
                            in: RoundedRectangle(cornerRadius: 9, style: .continuous)
                        )

                    VStack(alignment: .leading, spacing: 3) {
                        Text(name)
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                            .foregroundStyle(.primaryText)

                        Text("Opens in this app")
                            .font(.system(size: 13, weight: .medium, design: .rounded))
                            .foregroundStyle(.secondaryText)
                    }

                    Spacer(minLength: 12)

                    // 蓝色只落在图标与这个 chevron 上：在 `surfaceElevated` 上做小面积强调够用，
                    // 而蓝色正文只有 4.13:1，达不到正文标准 /
                    // Blue stays on the glyph and this chevron: as a small accent on
                    // `surfaceElevated` it reads fine, while blue body copy only reaches 4.13:1
                    Image(systemName: "chevron.right")
                        .font(.system(size: 16, weight: .bold))
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
            .accessibilityLabel("\(name), opens in this app")
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
