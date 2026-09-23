//
//  WishlistRowView.swift
//  steaL
//
//  Created by yijiang on 2026/9/23.
//

import SwiftUI

/// Wishlist 的一行：价签形封面 + 名称 + 加入日期 + 推页箭头 /
/// One wishlist row: tag-shaped artwork, name, date added and a push chevron
struct WishlistRowView: View {

    /// 要显示的收藏快照 / The saved snapshot to show
    let item: WishlistItem

    /// 封面尺寸，按 Steam header 的 460 × 215 比例 / Artwork size, in Steam's 460 × 215 header ratio
    private let artworkSize = CGSize(width: 128, height: 60)

    var body: some View {
        HStack(spacing: 14) {
            artwork

            VStack(alignment: .leading, spacing: 4) {
                Text(item.name)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.primaryText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                Text(addedText)
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundStyle(.secondaryText)
            }

            Spacer(minLength: 8)

            // HIG：能推入下一层的行用 disclosure indicator / HIG: rows that drill in show a disclosure indicator
            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }

    // MARK: - 封面 / Artwork

    /// 裁成价签形的封面，绳孔透出页面底色 / Artwork clipped to a price tag; the page background shows through the hole
    private var artwork: some View {
        Color.imagePlaceholder
            .overlay {
                RetryingAsyncImage(url: item.headerImageURL.flatMap(URL.init(string:))) { phase in
                    if let image = phase.image {
                        image
                            .resizable()
                            .scaledToFill()
                    } else {
                        Image(systemName: phase.error == nil ? "photo" : "photo.badge.exclamationmark")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(.secondaryText)
                    }
                }
            }
            .frame(width: artworkSize.width, height: artworkSize.height)
            .clipShape(PriceTagShape(), style: FillStyle(eoFill: true))
            // 细描边让深色封面在深色底上也有边界 / A hairline keeps dark artwork distinct on a dark background
            .overlay {
                PriceTagShape()
                    .stroke(Color.primaryText.opacity(0.12), lineWidth: 0.5)
            }
            .accessibilityHidden(true)
    }

    // MARK: - 加入日期 / Date added

    /// 今天 / 昨天用词，更早用日期；不用相对时长，免得出现「23 hours ago」这种跨天的说法 /
    /// Words for today and yesterday, a date before that; not a relative duration, which reads oddly across midnight
    private var addedText: String {
        // 服务器还没确认写入时是 nil，其实就是刚刚 / nil until the server confirms the write, which means just now
        guard let addedAt = item.addedAt else { return "Added today" }
        let calendar = Calendar.current
        if calendar.isDateInToday(addedAt) { return "Added today" }
        if calendar.isDateInYesterday(addedAt) { return "Added yesterday" }

        let isThisYear = calendar.isDate(addedAt, equalTo: .now, toGranularity: .year)
        let style: Date.FormatStyle = isThisYear
            ? .dateTime.month(.abbreviated).day()
            : .dateTime.year().month(.abbreviated).day()
        return "Added \(addedAt.formatted(style))"
    }
}

#Preview {
    List(WishlistItem.previewSamples) { item in
        WishlistRowView(item: item)
            .listRowBackground(Color.clear)
    }
    .listStyle(.plain)
    .scrollContentBackground(.hidden)
    .background(Color.appBackground)
}
