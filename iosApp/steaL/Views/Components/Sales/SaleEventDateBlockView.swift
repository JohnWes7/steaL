//
//  SaleEventDateBlockView.swift
//  steaL
//
//  Created by yijiang on 2026/11/6.
//

import SwiftUI

/// 日历页样式的日期块：上面月份缩写，下面日期数字。
/// A calendar-page date block: the abbreviated month above the day number.
///
/// 大小用 `@ScaledMetric` 跟着 Dynamic Type 缩放，月份与日期按用户的 locale 和时区格式化。
/// The size scales with Dynamic Type through `@ScaledMetric`; month and day follow the user's locale and time zone.
struct SaleEventDateBlockView: View {

    /// 要显示的日期 / The date to show.
    let date: Date

    /// 月份文字与底色的颜色 / The colour of the month text and the fill.
    let tint: Color

    /// 方块边长，跟日期数字的字号一起缩放 / The block's side, scaling with the day number's text style.
    @ScaledMetric(relativeTo: .title2) private var side: CGFloat = 48

    var body: some View {
        VStack(spacing: 0) {
            Text(date, format: .dateTime.month(.abbreviated))
                .font(.system(.caption, design: .rounded, weight: .semibold))
                .foregroundStyle(tint)

            Text(date, format: .dateTime.day())
                .font(.system(.title2, design: .rounded, weight: .bold))
                .foregroundStyle(.primaryText)
        }
        .frame(width: side, height: side)
        // 圆角和边长的比例同 `IconTileView`（9 / 32）/ The corner-to-side ratio matches `IconTileView` (9 / 32)
        .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: side * 0.28, style: .continuous))
    }
}

#Preview {
    HStack(spacing: 12) {
        SaleEventDateBlockView(date: SaleEvent.previewWinterSale.start, tint: .brandBlue)
        SaleEventDateBlockView(date: SaleEvent.previewEpicSavings.end, tint: .brandGreen)
    }
    .padding()
}
