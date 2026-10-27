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

    /// 左侧长条宽度，和详情页价格行一致 / Leading bar width, matching the detail page's price rows
    private let barWidth: CGFloat = 5

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            saleBar

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
        // 按内容的理想高度排版，长条的 maxHeight 才会撑到整行高而不是无限高 /
        // Lay out at the content's ideal height so the bar's maxHeight fills the row rather than growing without bound
        .fixedSize(horizontal: false, vertical: true)
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        // 整行合成一个元素：label 是不变的名称，value 是会变的价格状态 /
        // The row is one element: the label is the fixed name, the value the changing price state
        .accessibilityElement(children: .combine)
        .accessibilityLabel(item.name)
        .accessibilityValue(spokenPrice)
    }

    // MARK: - 长条 / Leading bar

    /// 打折绿色、其余灰色，和折扣徽标同一个判断；打折已有徽标与划线价，颜色只是加强，不单靠颜色传达 /
    /// Green on sale, grey otherwise, using the badge's test; the badge and struck-through price already say it, so colour isn't the only cue
    private var saleBar: some View {
        let barColor = discountCut == nil ? Color.secondaryText.opacity(0.5) : Color.brandGreen

        return RoundedRectangle(cornerRadius: 3, style: .continuous)
            .fill(barColor)
            .frame(width: barWidth)
            .frame(maxHeight: .infinity)
            .accessibilityHidden(true)
    }

    // MARK: - 封面 / Artwork

    /// 按比例完整显示，不裁切；比例不同的旧图会在两侧留出占位底色 /
    /// Shown whole at its own ratio, never cropped; older art in another ratio leaves placeholder bars at the sides
    private var artwork: some View {
        Color.imagePlaceholder
            .overlay {
                RetryingAsyncImage(url: item.headerImageURL.flatMap(URL.init(string:))) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFit()
                    case .failure:
                        Image(systemName: "photo.badge.exclamationmark")
                            .font(.system(.body, weight: .semibold))
                            .foregroundStyle(.secondaryText)
                    default:
                        // 加载中（`.empty`）用系统转圈，与失败图标区分；封面只有 60pt 高，用小号 /
                        // A system spinner while loading (`.empty`), distinct from the failure icon; small, as the artwork is only 60pt tall
                        ProgressView()
                            .controlSize(.small)
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
            // 放在 `accessibilityHidden` 之后，VoiceOver 仍能读到折扣 / Added after `accessibilityHidden` so VoiceOver still reads the discount
            .overlay(alignment: .topLeading) {
                discountBadge
            }
    }

    /// 打折时的折扣百分比，其余情况是 `nil` / The discount percentage when on sale, otherwise `nil`
    private var discountCut: Int? {
        guard case .loaded(let deal) = price else { return nil }
        if deal.cut <= 0 {
            return nil
        }
        return deal.cut
    }

    /// 折扣徽标，颜色同首页卡片；直接用 API 的 `cut`，不本地重算 /
    /// Discount badge in the home cards' colour; uses the API's `cut` as-is rather than recomputing it
    @ViewBuilder
    private var discountBadge: some View {
        if let discountCut {
            Text("-\(discountCut)%")
                .font(.system(.caption2, design: .rounded, weight: .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Color.brandBlue, in: Capsule())
                .padding(4)
                // 封面固定 60pt 高，徽标放太大会盖住整张图 / The artwork is a fixed 60pt tall, so an oversized badge would cover it
                .dynamicTypeSize(...DynamicTypeSize.xxLarge)
                .accessibilityLabel("\(discountCut)% off")
        }
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

    // MARK: - 无障碍 / Accessibility

    /// VoiceOver 在名称之后读的 value：按价格状态读折扣与价格，与 `priceLine` 显示的内容一致。
    /// The value VoiceOver reads after the name: the discount and price for the price state, matching what `priceLine` shows.
    ///
    /// 价格先显示占位、加载完才有，会变，所以放 value；不合并时会先读左上角的折扣徽标、两个价格也分不清哪个是原价，所以按「折扣、现价、原价」重排。
    /// The price starts as a placeholder and arrives later, so it goes in the value; left alone the row reads the corner badge first
    /// and gives two prices without saying which is the regular one, so it is reordered as discount, price and regular price.
    private var spokenPrice: String {
        switch price {
        case .loading:
            return "Loading price"
        case .noPrice:
            return "No price"
        case .failed:
            return "Couldn't load price"
        case .loaded(let deal):
            let isOnSale = deal.regular.amountInt > deal.price.amountInt
            if !isOnSale {
                return deal.regular.spokenText
            }

            let currentText = deal.price.spokenText
            let regularText = deal.regular.spokenText
            let saleText = "\(currentText), was \(regularText)"
            // 徽标只在 `cut > 0` 时显示，读的时候同样 / The badge only shows when `cut > 0`, and so does the spoken discount
            if deal.cut <= 0 {
                return saleText
            }
            return "\(deal.cut)% off, \(saleText)"
        }
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
