//
//  WishlistRowView.swift
//  steaL
//
//  Created by yijiang on 2026/9/23.
//

import SwiftUI

/// Wishlist 的一行：圆角矩形封面 + 名称 + 加入日期，放在 `NavigationLink` 里用 /
/// One wishlist row: rounded-rectangle artwork, name and date added; meant to sit inside a `NavigationLink`
struct WishlistRowView: View {

    /// 要显示的收藏快照 / The saved snapshot to show
    let item: WishlistItem

    /// 封面尺寸，按 Steam header 的 460 × 215 比例 / Artwork size, in Steam's 460 × 215 header ratio
    private let artworkSize = CGSize(width: 128, height: 60)

    /// 封面圆角，和原来价签形的角一致 / Artwork corner radius, matching the corners of the earlier tag shape
    private let artworkCornerRadius: CGFloat = 8

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

            // 推页箭头由外层 `NavigationLink` 自动加，这里不画 / The outer `NavigationLink` adds the disclosure chevron, so none here
            Spacer(minLength: 8)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }

    // MARK: - 封面 / Artwork

    /// 裁成圆角矩形的封面；价签形（`PriceTagShape`）暂时不用，代码留着备选 /
    /// Artwork clipped to a rounded rectangle; the tag shape (`PriceTagShape`) is unused for now but kept as an option
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
            .clipShape(RoundedRectangle(cornerRadius: artworkCornerRadius, style: .continuous))
            // 细描边让深色封面在深色底上也有边界 / A hairline keeps dark artwork distinct on a dark background
            .overlay {
                RoundedRectangle(cornerRadius: artworkCornerRadius, style: .continuous)
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
    // 包进 `NavigationLink` 才看得到系统加的箭头 / Wrapped in `NavigationLink` so the system chevron shows
    NavigationStack {
        List(WishlistItem.previewSamples) { item in
            NavigationLink(value: item.appID) {
                WishlistRowView(item: item)
            }
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.appBackground)
    }
}
