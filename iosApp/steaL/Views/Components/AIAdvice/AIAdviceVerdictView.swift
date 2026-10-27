//
//  AIAdviceVerdictView.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import SwiftUI

/// AI 建议 sheet 的结论卡：分数圆环 + 结论 + AI 的一句话；整张 sheet 只有这里是视觉重点 /
/// The AI advice sheet's verdict card: score ring, verdict and the model's one-line reason;
/// the only visual focus on the sheet
struct AIAdviceVerdictView: View {

    /// AI 结果 / The advice
    let advice: DetailAIAdviceResponse

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let verdict = advice.scoredVerdict
        let tint = Self.tint(for: verdict)

        // 辅助功能字号下圆环放到文字上面，让一句话结论有整行宽度 / At accessibility sizes the ring sits above the text so the reason gets the full width
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 12))
            : AnyLayout(HStackLayout(alignment: .center, spacing: 16))

        layout {
            Gauge(value: Double(advice.score), in: 0...100) {
                Text("Score")
            } currentValueLabel: {
                Text(advice.score, format: .number)
                    .font(.system(.title3, design: .rounded, weight: .bold))
            }
            .gaugeStyle(.accessoryCircularCapacity)
            .tint(tint)

            VStack(alignment: .leading, spacing: 4) {
                Text(Self.title(for: verdict))
                    .font(.system(.title2, design: .rounded, weight: .bold))
                    .foregroundStyle(tint)

                Text(advice.headline)
                    .font(.system(.subheadline, design: .rounded))
                    .foregroundStyle(.primaryText)
            }
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
    }

    /// 结论的显示文字 / The verdict's label
    static func title(for verdict: DetailAIAdviceResponse.Verdict) -> String {
        switch verdict {
        case .buy: "Buy now"
        case .wait: "Wait"
        case .skip: "Skip"
        }
    }

    /// 结论的颜色：买是品牌绿，等是橙色，跳过用次要文字色 / Buy is brand green, wait orange, skip the secondary text colour
    private static func tint(for verdict: DetailAIAdviceResponse.Verdict) -> Color {
        switch verdict {
        case .buy: .brandGreen
        case .wait: .orange
        case .skip: .secondaryText
        }
    }
}

#Preview("Buy") {
    List {
        AIAdviceVerdictView(advice: .previewBuy)
    }
}

#Preview("Wait") {
    List {
        AIAdviceVerdictView(advice: .previewWait)
    }
}
