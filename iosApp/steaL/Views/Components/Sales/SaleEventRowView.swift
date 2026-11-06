//
//  SaleEventRowView.swift
//  steaL
//
//  Created by yijiang on 2026/11/6.
//

import SwiftUI

/// Sales 列表的一行：日期块 + 名字 + 商店与时间，点了在浏览器里打开来源网页。
/// One row of the Sales list: date block, name, store and timing; tapping opens the source page in the browser.
///
/// 进行中的活动显示结束日期和「还剩多久」，未开始的显示开始日期和持续几天（同 Apple Games 的 Happening Now）。
/// Ongoing events show the end date and time left; upcoming ones show the start date and length (as in Apple Games' Happening Now).
struct SaleEventRowView: View {

    /// 这一行的活动 / The row's event.
    let event: SaleEvent

    /// 是否已经开始、还没结束 / Whether the event has started and not ended.
    let isHappeningNow: Bool

    /// 交给系统在浏览器里打开网页 / Hands the page to the system to open in the browser.
    @Environment(\.openURL) private var openURL

    /// 算持续天数用的日历（带用户时区）/ The calendar used to count days (with the user's time zone).
    @Environment(\.calendar) private var calendar

    /// accessibility 字号下换成竖排 / Switches to a vertical layout at accessibility text sizes.
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        Button {
            openURL(event.sourceURL)
        } label: {
            let layout = dynamicTypeSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
                : AnyLayout(HStackLayout(spacing: 12))

            layout {
                SaleEventDateBlockView(date: blockDate, tint: blockTint)

                VStack(alignment: .leading, spacing: 2) {
                    // 按钮里当前色是 tint，写固定的 `Color.primary` / `Color.secondary` 才是正文色。
                    // Inside a button the current colour is the tint, so fixed system colours keep body text colours.
                    Text(event.name)
                        .font(.system(.body, design: .rounded, weight: .semibold))
                        .foregroundStyle(Color.primary)

                    detailText
                        .font(.system(.footnote, design: .rounded))
                        .foregroundStyle(Color.secondary)
                }

                Spacer(minLength: 0)

                Image(systemName: "arrow.up.forward.square")
                    .foregroundStyle(.secondaryText)
            }
            // 撑满整行、空白也可点，同 `AboutLinkRow`。
            // Fills the row so the blank area is tappable too, like `AboutLinkRow`.
            .contentShape(Rectangle())
        }
        .accessibilityHint("Opens outside steaL")
        // 打开外部网页，VoiceOver 读成 link 而不是 button / Opens a web page outside the app, so VoiceOver says link rather than button
        .accessibilityRemoveTraits(.isButton)
        .accessibilityAddTraits(.isLink)
    }

    // MARK: - 派生显示 / Derived display

    /// 日期块显示的日期：进行中是结束日，未开始是开始日。
    /// The date in the block: the end day when ongoing, the start day when upcoming.
    private var blockDate: Date {
        isHappeningNow ? event.end : event.start
    }

    /// 日期块的颜色：进行中用品牌绿（正在打折），未开始用品牌蓝。
    /// The block's colour: brand green when ongoing (on sale now), brand blue when upcoming.
    private var blockTint: Color {
        isHappeningNow ? .brandGreen : .brandBlue
    }

    /// 名字下面那一行：商店 + 还剩多久 / 持续几天。
    /// The line under the name: the store plus time left or length in days.
    private var detailText: Text {
        if isHappeningNow {
            let timeLeft = event.end.formatted(.relative(presentation: .named))
            return Text("\(storeName) · Ends \(timeLeft)")
        }

        // `inflect: true` 让系统按数量选单复数（Foundation 的自动语法一致）。
        // `inflect: true` lets the system pick singular or plural (Foundation's automatic grammar agreement).
        return Text("\(storeName) · ^[\(dayCount) day](inflect: true)")
    }

    /// 持续几天：按用户时区数开始日到结束日之间的天数。
    /// The length in days: the days between the start and end dates in the user's time zone.
    ///
    /// 按日历日而不是按 24 小时数：跨夏令时的活动少一小时，按小时算会少一天。
    /// Counts calendar days rather than 24-hour periods: an event across a DST change is an hour short and would lose a day.
    private var dayCount: Int {
        let startDay = calendar.startOfDay(for: event.start)
        let endDay = calendar.startOfDay(for: event.end)
        let components = calendar.dateComponents([.day], from: startDay, to: endDay)
        return components.day ?? 0
    }

    /// 商店的显示名；不认识的商店直接显示原始值。
    /// The store's display name; an unknown store shows its raw value.
    private var storeName: String {
        switch event.store {
        case .steam: "Steam"
        case .epic: "Epic Games Store"
        case .itchio: "itch.io"
        case .gog: "GOG"
        case .unknown(let rawValue): rawValue
        }
    }
}

#Preview {
    List {
        Section("Happening Now") {
            SaleEventRowView(event: .previewEpicSavings, isHappeningNow: true)
        }

        Section("Upcoming") {
            SaleEventRowView(event: .previewAutoBattlerFest, isHappeningNow: false)
            SaleEventRowView(event: .previewWinterSale, isHappeningNow: false)
            SaleEventRowView(event: .previewUnknownStore, isHappeningNow: false)
        }
    }
    .listStyle(.insetGrouped)
}
