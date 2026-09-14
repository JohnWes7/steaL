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

    /// 本体名称，Steam 没给试玩版名字时用它拼标题 /
    /// The base game's name, used for the title when Steam leaves the demo's own name empty
    let gameName: String

    /// Steam 给这个游戏列的试玩版 / The demos Steam lists for this game
    let demos: [SteamDemo]

    /// 用来打开外部的商店页 / Used to open the external store page
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(demos, id: \.self) { demo in
                demoBlock(demo)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 一块试玩版按钮 / One demo button
    private func demoBlock(_ demo: SteamDemo) -> some View {
        // Steam 很少填试玩版自己的名字（实测 6 个里只有 1 个有），空串时退回「本体名 + Demo」/
        // Steam rarely fills in the demo's own name, so an empty one falls back to the base game
        let name = demo.description.flatMap { $0.isEmpty ? nil : $0 } ?? "\(gameName) Demo"

        return Button {
            // 旧试玩版在 Steam 上会被重定向回本体，这里按它给的 appid 拼地址即可 /
            // Retired demos redirect back to the base game on Steam's side, so the appid is enough
            guard let appID = demo.appid,
                let url = URL(string: "https://store.steampowered.com/app/\(appID)/")
            else { return }

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

#Preview("有试玩版 / With demos") {
    VStack(alignment: .leading, spacing: 22) {
        DemoSectionView(
            gameName: "Probably Stolen - Cyberpunk Shopkeeper Sim",
            demos: [SteamDemo(appid: 4349200, description: "")]
        )

        DemoSectionView(
            gameName: "Factorio",
            demos: [SteamDemo(appid: 452280, description: "Factorio Demo")]
        )
    }
    .padding(20)
    .background(Color.appBackground)
}
