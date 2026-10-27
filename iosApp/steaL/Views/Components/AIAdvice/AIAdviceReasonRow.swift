//
//  AIAdviceReasonRow.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import SwiftUI

/// AI 建议的一条理由：立场图标 + 主题 + 正文 / One reason from the advice: stance icon, topic and text
struct AIAdviceReasonRow: View {

    /// AI 给的理由 / The model's reason
    let reason: DetailAIAdviceResponse.Reason

    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 2) {
                Text(topicTitle)
                    .font(.system(.subheadline, design: .rounded, weight: .semibold))
                    .foregroundStyle(.primaryText)

                Text(reason.text)
                    .font(.system(.subheadline, design: .rounded))
                    .foregroundStyle(.secondaryText)
            }
        } icon: {
            Image(systemName: symbolName)
                .foregroundStyle(tint)
                // 颜色和形状之外再给 VoiceOver 一个词 / A word for VoiceOver beyond colour and shape
                .accessibilityLabel(stanceName)
        }
        .accessibilityElement(children: .combine)
    }

    /// 主题的显示文字 / The topic's label
    private var topicTitle: String {
        switch reason.topic {
        case .price: "Price"
        case .reviews: "Reviews"
        case .players: "Players"
        case .risk: "Heads-up"
        }
    }

    /// 立场图标，用 SF Symbol 不用 emoji / The stance icon, an SF Symbol rather than an emoji
    private var symbolName: String {
        switch reason.stance {
        case .pro: "checkmark.circle.fill"
        case .caution: "exclamationmark.triangle.fill"
        case .con: "xmark.circle.fill"
        }
    }

    /// 立场颜色，沿用项目里好评绿 / 差评红的用法 / Stance colour, reusing the project's positive green and negative red
    private var tint: Color {
        switch reason.stance {
        case .pro: .brandGreen
        case .caution: .orange
        case .con: .red.opacity(0.75)
        }
    }

    /// 立场的读屏文字 / The stance for VoiceOver
    private var stanceName: String {
        switch reason.stance {
        case .pro: "In favour"
        case .caution: "Caution"
        case .con: "Against"
        }
    }
}

#Preview {
    List {
        ForEach(DetailAIAdviceResponse.previewWait.reasons, id: \.self) { reason in
            AIAdviceReasonRow(reason: reason)
        }
    }
}
