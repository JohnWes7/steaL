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

    /// 图片区域：顶部徽标与收藏、底部标签与标题；末尾的 `.clipped()` 把溢出切在框内 /
    /// Artwork area with overlays; the trailing `.clipped()` keeps the fill inside the frame
    private var artwork: some View {
        Color.clear
            .aspectRatio(2, contentMode: .fit)
            .overlay { banner }
            .overlay { scrim }
            .overlay(alignment: .top) { topBar }
            .overlay(alignment: .bottom) { titleBlock }
            .clipped()
    }

    /// 游戏大图。三种状态分开渲染，失败与加载中不再共用一个占位 /
    /// Game artwork, with the three phases rendered separately: loading and failure no longer
    /// share one placeholder
    @ViewBuilder
    private var banner: some View {
        RetryingAsyncImage(url: deal.wideArtworkURL) { phase in
            switch phase {
            case .success(let image):
                image
                    .resizable()
                    .scaledToFill()
            case .empty:
                placeholder
            case .failure:
                failedPlaceholder
            default:
                placeholder
            }
        }
    }

    /// 加载中占位，保证任何响应都能撑满图片区 / Loading placeholder that always fills the artwork area
    private var placeholder: some View {
        ZStack {
            Color.imagePlaceholder
            Image(systemName: "photo")
                .font(.system(size: 32, weight: .semibold))
                .foregroundStyle(.secondaryText)
        }
    }

    /// 加载失败占位，用不同图标与加载中区分；重试由外层 `RetryingAsyncImage` 负责 /
    /// Failure placeholder, told apart from the loading one by its icon; `RetryingAsyncImage` retries
    private var failedPlaceholder: some View {
        ZStack {
            Color.imagePlaceholder
            Image(systemName: "photo.badge.exclamationmark")
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
            if let qualityLabel {
                Text(qualityLabel)
                    .font(.system(size: 12, weight: .heavy))
                    .tracking(0.6)
                    .foregroundStyle(.brandGreen)
                    .environment(\.colorScheme, .dark)
            }

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
        .font(.system(size: 26, weight: .bold, design: .monospaced))
        .foregroundStyle(.brandGreen)
    }

    /// 划线原价，仅在确实低于原价时显示 / Struck-through regular price, only shown when it is actually higher
    @ViewBuilder
    private var regularPrice: some View {
        let regular = deal.deal.regular

        if regular.amountInt > deal.deal.price.amountInt {
            Text(regular.amount, format: .currency(code: regular.currency))
                .font(.system(size: 16, weight: .medium, design: .monospaced))
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

    /// 图片上的绿色标签，依次判断全史最低、近三月最低和激活平台 / Green tag for deal quality or activation platform
    private var qualityLabel: String? {
        let info = deal.deal

        if info.flag == "H" || info.flag == "N" || info.isHistoricalLow {
            return "ALL-TIME LOW"
        }

        if let threeMonthLow = info.historyLow3m,
           info.price.amountInt <= threeMonthLow.amountInt {
            return "3-MONTH LOW"
        }

        if info.isSteamActivation {
            return "STEAM"
        }

        return nil
    }
}

#Preview {
    ScrollView {
        DealCardView(deal: .previewSample, isFavorite: false, onToggleFavorite: {})
            .padding(14)
    }
    .background(Color.appBackground)
}
