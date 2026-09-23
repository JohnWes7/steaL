//
//  WishlistRowView.swift
//  steaL
//
//  Created by yijiang on 2026/9/23.
//

import SwiftUI

/// Wishlist 的一行：完整显示的封面 + 名称 + 价格，放在 `NavigationLink` 里用 /
/// One wishlist row: uncropped artwork, name and price; meant to sit inside a `NavigationLink`
struct WishlistRowView: View {

    /// 要显示的收藏快照 / The saved snapshot to show
    let item: WishlistItem

    /// 现价；还没接价格 API，页面先传占位价 / Current price; the page passes a placeholder until the price API is wired up
    let price: ITADPrice

    /// 原价，高于现价就算在打折 / Regular price; higher than `price` means the game is on sale
    let regular: ITADPrice

    /// 封面尺寸，按 Steam header 的 460 × 215 比例 / Artwork size, in Steam's 460 × 215 header ratio
    private let artworkSize = CGSize(width: 128, height: 60)

    /// 封面圆角 / Artwork corner radius
    private let artworkCornerRadius: CGFloat = 8

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            artwork

            // 名称贴封面顶边、价格贴底边，和封面一样高 / Name hugs the artwork's top edge and price its bottom, matching its height
            VStack(alignment: .leading, spacing: 0) {
                Text(item.name)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundStyle(.primaryText)
                    .lineLimit(1)

                Spacer(minLength: 4)

                priceLine
            }
            .frame(height: artworkSize.height)

            // 推页箭头由外层 `NavigationLink` 自动加，这里不画 / The outer `NavigationLink` adds the disclosure chevron, so none here
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }

    // MARK: - 封面 / Artwork

    /// 按比例完整显示，不裁切；比例不同的旧图会在两侧留出占位底色 /
    /// Shown whole at its own ratio, never cropped; older art in another ratio leaves placeholder bars at the sides
    private var artwork: some View {
        Color.imagePlaceholder
            .overlay {
                RetryingAsyncImage(url: item.headerImageURL.flatMap(URL.init(string:))) { phase in
                    if let image = phase.image {
                        image
                            .resizable()
                            .scaledToFit()
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

    // MARK: - 价格 / Price

    /// 打折时绿色现价 + 灰色划线原价，不打折只显示原价 / On sale: green price plus grey struck-through regular; otherwise just the regular price
    @ViewBuilder
    private var priceLine: some View {
        let isOnSale = regular.amountInt > price.amountInt

        if isOnSale {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                priceText(price)
                    .font(.system(size: 15, weight: .bold, design: .monospaced))
                    .foregroundStyle(.brandGreen)

                priceText(regular)
                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                    .foregroundStyle(.secondaryText)
                    .strikethrough()
            }
        } else {
            priceText(regular)
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .foregroundStyle(.primaryText)
        }
    }

    /// 金额为 0 显示 `Free`，和首页一致 / Zero reads as `Free`, as on the home page
    private func priceText(_ value: ITADPrice) -> Text {
        if value.amountInt == 0 {
            return Text("Free")
        }
        return Text(value.amount, format: .currency(code: value.currency))
    }
}

#Preview {
    // 包进 `NavigationLink` 才看得到系统加的箭头 / Wrapped in `NavigationLink` so the system chevron shows
    NavigationStack {
        List(WishlistItem.previewSamples) { item in
            NavigationLink(value: item.appID) {
                WishlistRowView(item: item, price: item.mockPrice, regular: item.mockRegular)
            }
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.appBackground)
    }
}
