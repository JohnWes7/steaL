//
//  DealCardView.swift
//  steaL
//
//  Created by yijiang on 2026/9/10.
//

import SwiftUI

/// 主页优惠大卡片：图片 + 折扣徽标 + 收藏按钮 + 质量标签 + 标题 + 价格行 /
/// Large deal card for the home screen: artwork, discount badge, favourite toggle, quality tag, title and price row
struct DealCardView: View {

    /// 要展示的游戏及其当前最佳优惠 / The game and its current best deal
    let deal: ITADDeal

    /// 是否已收藏 / Whether the user has favourited this game
    let isFavorite: Bool

    /// 点击收藏时的回调。状态由父视图持有，避免 `LazyVStack` 回收卡片时丢失 /
    /// Called when the favourite button is tapped; state lives in the parent so `LazyVStack` recycling cannot drop it
    let onToggleFavorite: () -> Void

    /// 卡片圆角，图片区和价格行共用同一套裁切 / Shared corner radius, clipped once for the whole card
    private let cornerRadius: CGFloat = 20

    var body: some View {
        VStack(spacing: 0) {
            artwork
            priceRow
        }
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }

    // MARK: - 图片区 / Artwork

    /// 图片区域。上下各叠一层内容：顶部徽标与收藏，底部标签与标题 /
    /// Artwork area with overlays: badges on top, quality tag and title pinned to the bottom
    private var artwork: some View {
        Color.clear
            .aspectRatio(2, contentMode: .fit)
            .overlay { banner }
            .overlay { scrim }
            .overlay(alignment: .top) { topBar }
            .overlay(alignment: .bottom) { titleBlock }
    }

    /// 游戏大图，加载中与失败时都回落到同一种占位样式 /
    /// Game artwork, falling back to the same placeholder while loading and on failure
    @ViewBuilder
    private var banner: some View {
        AsyncImage(url: bannerURL) { phase in
            switch phase {
            case .success(let image):
                image
                    .resizable()
                    .scaledToFill()
            default:
                placeholder
            }
        }
    }

    /// 无图占位，保证任何响应都能撑满图片区 / Placeholder that always fills the artwork area
    private var placeholder: some View {
        ZStack {
            Color.saleSurface
            Image(systemName: "photo")
                .font(.system(size: 32, weight: .semibold))
                .foregroundStyle(.secondaryText)
        }
    }

    /// 上下压暗的渐变，让白色文字和徽标在任何图片上都保持可读 /
    /// Top and bottom scrim so white text and badges stay readable on any artwork
    private var scrim: some View {
        LinearGradient(
            stops: [
                .init(color: .black.opacity(0.45), location: 0),
                .init(color: .black.opacity(0), location: 0.28),
                .init(color: .black.opacity(0.10), location: 0.55),
                .init(color: .black.opacity(0.85), location: 1),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    /// 图片顶部的折扣徽标与收藏按钮 / Discount badge and favourite button across the top of the artwork
    private var topBar: some View {
        HStack(alignment: .top) {
            discountBadge
            Spacer(minLength: 0)
            favoriteButton
        }
        .padding(.horizontal, 14)
        .padding(.top, 14)
    }

    /// 折扣徽标。直接使用 API 的 `cut`，不做本地重算，避免与服务端舍入不一致 /
    /// Discount badge using the API's `cut` as-is; recomputing locally risks disagreeing with the server
    @ViewBuilder
    private var discountBadge: some View {
        if deal.deal.cut > 0 {
            Text("-\(deal.deal.cut)%")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .padding(.horizontal, 13)
                .padding(.vertical, 7)
                .background(Color.brandBlue, in: Capsule())
        }
    }

    /// 收藏按钮，叠在图片上没有底板，靠阴影保证对比度 /
    /// Favourite button drawn straight on the artwork; a shadow keeps it legible without a backing plate
    private var favoriteButton: some View {
        Button(action: onToggleFavorite) {
            Image(systemName: isFavorite ? "heart.fill" : "heart")
                .font(.system(size: 23, weight: .semibold))
                .foregroundStyle(isFavorite ? Color.brandBlue : Color.white)
                .shadow(color: .black.opacity(0.45), radius: 5, y: 1)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isFavorite ? "Remove from favourites" : "Add to favourites")
    }

    /// 图片底部的质量标签与标题 / Quality tag and title pinned over the bottom of the artwork
    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(qualityLabel)
                .font(.system(size: 12, weight: .heavy))
                .tracking(0.6)
                .foregroundStyle(.brandGreen)

            Text(deal.title)
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(.white)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
    }

    // MARK: - 价格行 / Price row

    /// 底部价格行：左侧当前价与原价，右侧购买商店 / Bottom row with current and regular price on the left, shop on the right
    private var priceRow: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 1) {
                currentPrice
                regularPrice
            }

            Spacer(minLength: 0)

            shopPill
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background {
            Rectangle()
                .fill(.ultraThinMaterial)
                .overlay(Color.appBackground.opacity(0.4))  // 想更暗就调大
        }
    }

    /// 当前价，免费游戏显示 `Free` / Current price, rendered as `Free` when there is nothing to pay
    @ViewBuilder
    private var currentPrice: some View {
        let price = deal.deal.price

        Group {
            if price.amountInt == 0 {
                Text("Free")
            } else {
                Text(price.amount, format: .currency(code: price.currency))
            }
        }
        .font(.system(size: 26, weight: .heavy, design: .rounded))
        .foregroundStyle(.brandGreen)
    }

    /// 划线原价，仅在确实低于原价时显示 / Struck-through regular price, only shown when it is actually higher
    @ViewBuilder
    private var regularPrice: some View {
        let regular = deal.deal.regular

        if regular.amountInt > deal.deal.price.amountInt {
            Text(regular.amount, format: .currency(code: regular.currency))
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(.secondaryText)
                .strikethrough()
        }
    }

    /// 购买商店标签。`/deals/v2` 每个游戏只返回一个最佳价格，因此这里恒为一个商店 /
    /// Shop tag. `/deals/v2` returns a single best deal per game, so there is only ever one shop here
    private var shopPill: some View {
        Text(deal.deal.shop.name)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.secondaryText)
            .lineLimit(1)
            .padding(.horizontal, 13)
            .padding(.vertical, 8)
            .background(Color.white.opacity(0.08), in: Capsule())
    }

    // MARK: - 派生数据 / Derived values

    /// 按清晰度从高到低挑选可用的图片地址，末尾回落到竖版封面 /
    /// Picks the highest resolution image URL available, falling back to boxart last
    private var bannerURL: URL? {
        let candidates = [
            deal.assets.banner600,
            deal.assets.banner400,
            deal.assets.banner300,
            deal.assets.banner145,
            deal.assets.boxart,
        ]

        return candidates
            .compactMap { $0 }
            .compactMap { URL(string: $0) }
            .first
    }

    /// 图片上的绿色标签。`/deals/v2` 不返回游戏类型，因此这里用真实可得的优惠质量信息替代，
    /// 依次判断全史最低、近三月最低，最后回落到激活平台 /
    /// Green tag. `/deals/v2` has no genre field, so this reports real deal quality instead:
    /// all-time low, then three-month low, then the activation platform
    private var qualityLabel: String {
        let info = deal.deal

        if info.flag == "H" || info.isHistoricalLow {
            return "ALL-TIME LOW"
        }

        if let threeMonthLow = info.historyLow3m,
           info.price.amountInt <= threeMonthLow.amountInt {
            return "3-MONTH LOW"
        }

        if info.isSteamActivation {
            return "STEAM"
        }

        return deal.type.uppercased()
    }
}

#if DEBUG
private extension ITADDeal {

    /// 预览用示例数据，字段结构对齐 ITAD 真实响应，图片留空以便检查占位样式 /
    /// Sample data for previews, shaped like a real ITAD response with images left empty to exercise the placeholder
    static let previewSample = ITADDeal(
        id: "01973dd3-e247-712c-aaee-acff181a3175",
        slug: "cyberpunk-2077",
        title: "Cyberpunk 2077",
        type: "game",
        mature: true,
        assets: ITADAssets(
            boxart: "https://assets.isthereanydeal.com/018d937f-3a1d-720b-aafa-2c1f21404d3f/boxart.jpg?t=1788299733",
            banner145: "https://assets.isthereanydeal.com/018d937f-3a1d-720b-aafa-2c1f21404d3f/banner145.jpg?t=1788299733",
            banner300: "https://assets.isthereanydeal.com/018d937f-3a1d-720b-aafa-2c1f21404d3f/banner300.jpg?t=1788299733",
            banner400: "https://assets.isthereanydeal.com/018d937f-3a1d-720b-aafa-2c1f21404d3f/banner400.jpg?t=1788299733",
            banner600: "https://assets.isthereanydeal.com/018d937f-3a1d-720b-aafa-2c1f21404d3f/banner600.jpg?t=1788299733"
        ),
        deal: ITADDealInfo(
            shop: ITADShopRef(id: 61, name: "Steam"),
            price: ITADPrice(amount: 23.99, amountInt: 2399, currency: "AUD"),
            regular: ITADPrice(amount: 59.99, amountInt: 5999, currency: "AUD"),
            cut: 60,
            voucher: nil,
            storeLow: ITADPrice(amount: 23.99, amountInt: 2399, currency: "AUD"),
            historyLow: ITADPrice(amount: 27.99, amountInt: 2799, currency: "AUD"),
            historyLow1y: ITADPrice(amount: 29.99, amountInt: 2999, currency: "AUD"),
            historyLow3m: ITADPrice(amount: 29.99, amountInt: 2999, currency: "AUD"),
            flag: "H",
            drm: [ITADPlatformRef(id: 61, name: "Steam")],
            platforms: [ITADPlatformRef(id: 1, name: "Windows")],
            timestamp: "2026-09-01T19:15:45+02:00",
            expiry: nil,
            url: "https://isthereanydeal.com/"
        )
    )
}
#endif

#Preview {
    ScrollView {
        DealCardView(deal: .previewSample, isFavorite: false, onToggleFavorite: {})
            .padding(14)
    }
    .background(Color.appBackground)
}
