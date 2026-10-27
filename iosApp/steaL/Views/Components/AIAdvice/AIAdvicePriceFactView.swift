//
//  AIAdvicePriceFactView.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import SwiftUI

/// AI 建议 sheet 的价格事实：一行现价 + 折扣 + 离史低多远，数字由 App 直接给、不经过 AI /
/// The AI advice sheet's price fact: current price, discount and distance from the lows,
/// straight from the app's data and never through the model
///
/// 原价、三档低价明细不列：sheet 盖在详情页上，`PriceSectionView` 已经有了 /
/// No regular price or low-price breakdown: the sheet sits over the detail page, whose `PriceSectionView` has them
struct AIAdvicePriceFactView: View {

    /// 最便宜的 Steam 激活 deal，和 AI 拿到的是同一份 / The cheapest Steam-activating deal, the same one the model saw
    let deal: ITADPriceDeal
    /// ITAD 跨商店的历史低价 / ITAD's historical lows across all stores
    let historyLow: ITADPriceHistory

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    /// 离史低多远的标签 / The label for how close the price is to its lows
    private struct LowLabel {
        let text: String
        /// 是不是某一档低价，决定颜色 / Whether it hits one of the lows, which decides the colour
        let isLow: Bool
    }

    /// 优惠剩这么久以内才提示结束时间 / Only mention the end of a sale this close to it
    private static let saleEndWarning: TimeInterval = 3 * 24 * 60 * 60

    var body: some View {
        // 辅助功能字号下标签换到下一行，不和价格挤一行 / At accessibility sizes the label drops below the price
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 4))
            : AnyLayout(HStackLayout(alignment: .firstTextBaseline, spacing: 8))

        VStack(alignment: .leading, spacing: 4) {
            layout {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("\(priceText) at \(deal.shop.name)")
                        .font(.system(.headline, design: .rounded))
                        .foregroundStyle(.primaryText)

                    if deal.cut > 0 {
                        Text("-\(deal.cut)%")
                            .font(.system(.subheadline, design: .rounded, weight: .semibold))
                            .foregroundStyle(.brandBlue)
                    }
                }

                if !dynamicTypeSize.isAccessibilitySize {
                    Spacer(minLength: 0)
                }

                if let lowLabel {
                    Text(lowLabel.text)
                        .font(.system(.subheadline, design: .rounded, weight: .semibold))
                        .foregroundStyle(lowLabel.isLow ? Color.brandGreen : Color.secondaryText)
                }
            }

            if let saleEndText {
                Text(saleEndText)
                    .font(.system(.footnote, design: .rounded))
                    .foregroundStyle(.secondaryText)
            }
        }
        .accessibilityElement(children: .combine)
    }

    /// 现价；0 显示 Free / The current price; zero reads Free
    private var priceText: String {
        let price = deal.price
        if price.amountInt == 0 {
            return "Free"
        }
        return price.amount.formatted(.currency(code: price.currency))
    }

    /// 从上往下判断，命中第一档就停；都不是就写离史低还差多少 /
    /// Checked top to bottom, stopping at the first hit; otherwise says how far above the all-time low it is
    private var lowLabel: LowLabel? {
        let amount = deal.price.amountInt

        if let allTime = historyLow.all, amount <= allTime.amountInt {
            return LowLabel(text: "All-time low", isLow: true)
        }

        if let lastYear = historyLow.y1, amount <= lastYear.amountInt {
            return LowLabel(text: "1-year low", isLow: true)
        }

        if let last3Months = historyLow.m3, amount <= last3Months.amountInt {
            return LowLabel(text: "3-month low", isLow: true)
        }

        guard let allTime = historyLow.all else { return nil }

        let difference = deal.price.amount - allTime.amount
        let differenceText = difference.formatted(.currency(code: allTime.currency))
        return LowLabel(text: "\(differenceText) above all-time low", isLow: false)
    }

    /// 优惠 3 天内结束才提示，如 "Sale ends in 2 days" / Only when the sale ends within 3 days, e.g. "Sale ends in 2 days"
    private var saleEndText: String? {
        guard let expiry = deal.expiry else { return nil }
        guard let endDate = try? Date(expiry, strategy: .iso8601) else { return nil }

        let remaining = endDate.timeIntervalSinceNow
        guard remaining > 0, remaining < Self.saleEndWarning else { return nil }

        let relativeText = endDate.formatted(.relative(presentation: .named))
        return "Sale ends \(relativeText)"
    }
}

#Preview {
    let game = ITADPriceGame.previewSample
    List {
        if let deal = game.bestSteamDeal {
            AIAdvicePriceFactView(deal: deal, historyLow: game.historyLow)
        }
    }
}
