//
//  AboutLinkRow.swift
//  steaL
//
//  Created by yijiang on 2026/10/15.
//

import SwiftUI

/// 关于页里打开外部网页的一行。
/// A row on the About page that opens an external web page.
///
/// 离开 App 用 `arrow.up.forward.square`，和 Demo 入口一致；`chevron.right` 留给 App 内推页。
/// Leaving the app uses `arrow.up.forward.square`, like the demo entry; `chevron.right` is kept for in-app pushes.
struct AboutLinkRow: View {

    /// 行标题 / The row title.
    let title: String

    /// 标题下面的一句说明 / The one-line description under the title.
    let subtitle: String

    /// 左侧方块的 SF Symbol / The SF Symbol in the leading tile.
    let icon: String

    /// 左侧方块的底色 / The leading tile's fill colour.
    let tint: Color

    /// 要打开的网址 / The address to open.
    let urlString: String

    /// 用系统浏览器打开网址 / Opens the address in the system browser.
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button {
            guard let url = URL(string: urlString) else {
                return
            }

            openURL(url)
        } label: {
            HStack(spacing: 12) {
                IconTileView(icon: icon, tint: tint)

                VStack(alignment: .leading, spacing: 2) {
                    // 按钮里当前色是 tint，写固定的 `Color.primary` / `Color.secondary` 才是正文色。
                    // Inside a button the current colour is the tint, so fixed system colours keep body text colours.
                    Text(title)
                        .foregroundStyle(Color.primary)

                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(Color.secondary)
                }

                Spacer()

                Image(systemName: "arrow.up.forward.square")
                    .foregroundStyle(.secondaryText)
            }
            // 撑满整行、空白也可点，同 Profile 的按钮行。
            // Fills the row so the blank area is tappable too, like Profile's button rows.
            .contentShape(Rectangle())
        }
        .accessibilityHint("Opens in your browser")
    }
}

#Preview {
    List {
        AboutLinkRow(
            title: "IsThereAnyDeal",
            subtitle: "Prices, price history and giveaways",
            icon: "tag.fill",
            tint: .brandBlue,
            urlString: "https://isthereanydeal.com"
        )
    }
}
