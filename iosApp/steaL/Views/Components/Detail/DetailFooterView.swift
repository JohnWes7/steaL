//
//  DetailFooterView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 详情页底部的标识信息：Steam App ID 与 ITAD UUID /
/// The identity footer at the bottom of the detail page: the Steam App ID and the ITAD UUID
struct DetailFooterView: View {

    /// Steam 游戏 App ID / The Steam app ID
    let appID: Int

    /// ITAD 游戏 UUID，ITAD 没收录这个游戏时为空 / The ITAD game UUID, `nil` when ITAD does not track the game
    let itadID: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Divider()

            idRow(label: "Steam App ID", value: "\(appID)")

            if let itadID {
                idRow(label: "ITAD ID", value: itadID)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 一行标识：标签在上、等宽值在下，长 UUID 不会被挤成两行 /
    /// One identity row, label above a monospaced value so a long UUID is not squeezed
    private func idRow(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(.footnote, design: .rounded, weight: .medium))
                .foregroundStyle(.secondaryText)

            Text(value)
                .font(.system(.subheadline, weight: .medium))
                .foregroundStyle(.secondaryText)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label): \(value)")
    }
}

#Preview("两个都有 / Both IDs") {
    DetailFooterView(appID: 620, itadID: "018d937f-0f5c-72f6-9b1e-2e5b3c4d5e6f")
        .padding(20)
        .background(Color.appBackground)
}

#Preview("没有 ITAD ID / No ITAD ID") {
    DetailFooterView(appID: 620, itadID: nil)
        .padding(20)
        .background(Color.appBackground)
}
