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

    /// 这一行的价格状态，由 `WishlistViewModel` 给 / This row's price state, supplied by `WishlistViewModel`
    let price: WishlistViewModel.PriceState

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
                    .font(.system(.callout, design: .rounded, weight: .semibold))
                    .foregroundStyle(.primaryText)
                    .lineLimit(1)

                Spacer(minLength: 4)

                priceLine
            }
            // 默认和封面一样高，字号放大时允许长高 / As tall as the artwork by default, but free to grow with larger text
            .frame(minHeight: artworkSize.height)

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
                            .font(.system(.body, weight: .semibold))
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

    /// 按价格状态切换：占位、价格、没价格、失败 / Switches on the price state: placeholder, price, no price or failure
    @ViewBuilder
    private var priceLine: some View {
        switch price {
        case .loading:
            // 先占住位置，价格回来时不跳动（HIG Loading：尽快显示占位内容）/
            // Hold the space so nothing jumps when the price lands (HIG Loading: show placeholder content early)
            Text(verbatim: "$00.00")
                .font(.system(.subheadline, design: .monospaced, weight: .bold))
                .redacted(reason: .placeholder)
        case .loaded(let deal):
            dealPrice(deal)
        case .noPrice:
            Text("No price")
                .font(.system(.subheadline, design: .rounded, weight: .medium))
                .foregroundStyle(.secondaryText)
        case .failed:
            Label("Couldn't load price", systemImage: "exclamationmark.triangle")
                .font(.system(.subheadline, design: .rounded, weight: .medium))
                .foregroundStyle(.secondaryText)
        }
    }

    /// 打折时绿色现价 + 灰色划线原价，不打折只显示原价；两个价都来自同一条 deal /
    /// On sale: green price plus grey struck-through regular; otherwise just the regular price. Both come from the same deal
    @ViewBuilder
    private func dealPrice(_ deal: ITADPriceDeal) -> some View {
        let isOnSale = deal.regular.amountInt > deal.price.amountInt

        if isOnSale {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                priceText(deal.price)
                    .font(.system(.subheadline, design: .monospaced, weight: .bold))
                    .foregroundStyle(.brandGreen)

                priceText(deal.regular)
                    .font(.system(.caption, design: .monospaced, weight: .medium))
                    .foregroundStyle(.secondaryText)
                    .strikethrough()
            }
        } else {
            priceText(deal.regular)
                .font(.system(.subheadline, design: .monospaced, weight: .bold))
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

#if DEBUG
extension WishlistViewModel.PriceState {

    /// 预览用：四种状态各一个，和 `WishlistItem.previewSamples` 前四条配对 /
    /// Preview states, one of each, paired with the first four `WishlistItem.previewSamples`
    static let previewStates: [WishlistViewModel.PriceState] = [
        // 样例里第一条是 Fanatical 的 Steam key，打折中 / The sample's first deal is a discounted Fanatical Steam key
        .loaded(ITADPriceGame.previewSample.deals[0]),
        .loading,
        .noPrice,
        .failed,
    ]
}
#endif

#Preview {
    // 包进 `NavigationLink` 才看得到系统加的箭头 / Wrapped in `NavigationLink` so the system chevron shows
    NavigationStack {
        List(0..<4, id: \.self) { index in
            let item = WishlistItem.previewSamples[index]
            let price = WishlistViewModel.PriceState.previewStates[index]
            NavigationLink(value: item.appID) {
                WishlistRowView(item: item, price: price)
            }
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.appBackground)
    }
}
