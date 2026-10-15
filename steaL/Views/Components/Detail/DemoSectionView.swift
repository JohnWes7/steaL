//
//  DemoSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 有试玩版时的入口：与 `FullGameSectionView` 同一套行卡片，绿色 = 出 App 去 Steam 网页
/// （ITAD 不收录试玩版，比价没意义）/
/// The demo entry: the same row card as `FullGameSectionView`, where green means the tap leaves
/// the app for the Steam page (ITAD does not track demos, so a price comparison is pointless)
struct DemoSectionView: View {

    /// 试玩版在 Steam 上的 appid / The demo's appid on Steam
    let appID: Int

    /// 试玩版标题，直接用 Steam 给的名字 / The demo's title, taken from Steam as-is
    let name: String

    /// 用来打开外部的商店页 / Used to open the external store page
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("DEMO")
                .font(.system(.footnote, weight: .bold))
                .foregroundStyle(.brandGreen)

            Button {
                // 退役的试玩版会被 Steam 重定向回本体，拼 appid 就够了 / Retired demos redirect to the base game
                guard let url = URL(string: "https://store.steampowered.com/app/\(appID)/") else {
                    return
                }

                openURL(url)
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "arrow.down.circle.fill")
                        .font(.system(.subheadline, weight: .bold))
                        .foregroundStyle(.appBackground)
                        .frame(width: 32, height: 32)
                        .background(
                            Color.brandGreen,
                            in: RoundedRectangle(cornerRadius: 9, style: .continuous)
                        )

                    VStack(alignment: .leading, spacing: 3) {
                        Text("\(name)")
                            .font(.system(.callout, design: .rounded, weight: .semibold))
                            .foregroundStyle(.primaryText)

                        Text("Opens the Steam store page")
                            .font(.system(.footnote, design: .rounded, weight: .medium))
                            .foregroundStyle(.secondaryText)
                    }

                    Spacer(minLength: 12)

                    // 与完整游戏入口同形状，靠尾符号与颜色区分出 / 入 App /
                    // Same shape as the full-game entry; the trailing mark and its colour tell exit from push
                    Image(systemName: "arrow.up.forward.square")
                        .font(.system(.callout, weight: .bold))
                        .foregroundStyle(.brandGreen)
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
            .accessibilityHint("Opens the Steam store page outside steaL")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview("试玩版入口 / Demo entry") {
    VStack(alignment: .leading, spacing: 22) {
        DemoSectionView(
            appID: 4349200,
            name: "Probably Stolen - Cyberpunk Shopkeeper Sim Demo"
        )

        DemoSectionView(appID: 452280, name: "Factorio Demo")
    }
    .padding(20)
    .background(Color.appBackground)
}
