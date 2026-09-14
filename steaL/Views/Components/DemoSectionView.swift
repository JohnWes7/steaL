//
//  DemoSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 有试玩版时的入口：一块实心绿色按钮，点开直接交给浏览器 /
/// The demo entry shown when a demo exists: one solid green block that hands off to the browser
///
/// ITAD 不收录试玩版，比价对它没有意义，所以这里不推详情页，而是跳 Steam 商店页 /
/// ITAD does not track demos, so pricing is meaningless for them; this opens the Steam store page
/// instead of pushing another detail screen
struct DemoSectionView: View {

    /// 试玩版在 Steam 上的 appid / The demo's appid on Steam
    let appID: Int

    /// 试玩版标题，直接用 Steam 给的名字 / The demo's title, taken from Steam as-is
    let name: String

    /// 用来打开外部的商店页 / Used to open the external store page
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button {
            // 退役的试玩版会被 Steam 重定向回本体，所以照 appid 拼地址就够了 /
            // Retired demos redirect back to the base game on Steam's side, so the appid is enough
            guard let url = URL(string: "https://store.steampowered.com/app/\(appID)/") else { return }

            openURL(url)
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Download \(name)")
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundStyle(.appBackground)

                    Text("Opens the Steam store page")
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundStyle(Color.appBackground.opacity(0.72))
                }

                Spacer(minLength: 12)

                Image(systemName: "arrow.up.forward.square")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.appBackground)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                Color.brandGreen,
                in: RoundedRectangle(cornerRadius: 16, style: .continuous)
            )
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Download \(name), opens the Steam store page")
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
