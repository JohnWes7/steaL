//
//  DealGridCardView.swift
//  steaL
//
//  Created by yijiang on 2026/9/11.
//

import SwiftUI

/// 网格里的小卡片：竖版 boxart 铺满整卡，折扣徽标与收藏在顶部，标题与玻璃价格行压底部 /
/// Compact grid card: portrait box art with the discount badge and favourite on top, and the
/// title plus a glass price row along the bottom
struct DealGridCardView: View {

    /// 要展示的游戏及其当前最佳优惠 / The game and its current best deal
    let deal: ITADDeal

    /// 是否已收藏 / Whether the user has favourited this game
    let isFavorite: Bool

    /// 能否收藏，没有 Steam appID 时变暗禁用 / Whether it can be favourited; dimmed and disabled without a Steam appID
    let isFavoriteEnabled: Bool

    /// 点击收藏时的回调，状态同样由父视图持有 /
    /// Called when the favourite is tapped; state lives in the parent as well
    let onToggleFavorite: () -> Void

    /// 比 hero 卡略小的圆角 / Slightly tighter corner radius than the hero card
    private let cornerRadius: CGFloat = 16

    /// 竖版封面比例；2:3 与 Steam 库封面、ITAD boxart 一致，`scaledToFill` 因此不裁切 /
    /// Portrait ratio, matching Steam capsules and ITAD box art so `scaledToFill` crops nothing
    private let artworkAspectRatio: CGFloat = 2.0 / 3.0

    var body: some View {
        artwork
    }

    // MARK: - 图片区 / Artwork

    /// 整张卡片就是这块图片区域：封面铺满，标题与玻璃价格行依次叠在底部 /
    /// The whole card is this artwork area: the cover fills it while the title and the glass
    /// price row stack along the bottom
    private var artwork: some View {
        Color.clear
            .aspectRatio(artworkAspectRatio, contentMode: .fit)
            .overlay { boxArt }
            .overlay { scrim }
            // 叠加层都压在封面与渐变上，背景始终偏暗，所以固定用 dark 取色；封面占位不在其中，跟随系统外观 /
            // The overlays always sit on the artwork and its dark scrim, so they resolve dark colours;
            // the box-art placeholder is left out so it follows the system appearance
            .overlay(alignment: .top) { topBar.environment(\.colorScheme, .dark) }
            .overlay(alignment: .bottom) { bottomOverlay.environment(\.colorScheme, .dark) }
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }

    /// 竖版封面。三种状态分开渲染，失败与加载中不再共用一个占位 /
    /// Portrait cover art, with the three phases rendered separately: loading and failure no
    /// longer share one placeholder
    @ViewBuilder
    private var boxArt: some View {
        RetryingAsyncImage(url: deal.portraitArtworkURL) { phase in
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

    /// 加载中占位：占位底色上一个系统转圈，撑满卡片。
    /// Loading placeholder: a system spinner on the placeholder colour, filling the card.
    ///
    /// 与失败的图标区分开，一眼能看出还在加载。
    /// It sets loading apart from the failure icon, so an unfinished load reads as one at a glance.
    private var placeholder: some View {
        ZStack {
            Color.imagePlaceholder
            ProgressView()
        }
    }

    /// 加载失败占位，用不同图标与加载中区分。以后要接重试，就改在这一块 /
    /// Failure placeholder, told apart from the loading one by its icon. A retry affordance, when
    /// added, belongs here
    private var failedPlaceholder: some View {
        ZStack {
            Color.imagePlaceholder
            Image(systemName: "photo.badge.exclamationmark")
                .font(.system(.title, weight: .semibold))
                .foregroundStyle(.secondaryText)
        }
    }

    /// 底部压暗的渐变。除了保证白色标题可读，价格行的玻璃层也会采样到它 /
    /// Bottom-weighted scrim. Besides keeping the white title readable, the glass price row
    /// samples it as part of its backdrop
    private var scrim: some View {
        LinearGradient(
            stops: [
                .init(color: .black.opacity(0.3), location: 0),
                .init(color: .black.opacity(0), location: 0.22),
                .init(color: .black.opacity(0.10), location: 0.50),
                .init(color: .black.opacity(0.75), location: 1),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    /// 折扣徽标与收藏按钮。徽标颜色和位置与 hero 卡保持一致 /
    /// Discount badge and favourite button, matching the hero card's colour and corner
    private var topBar: some View {
        HStack {
            discountBadge
            Spacer(minLength: 0)
            favoriteButton
        }
        .padding(10)
    }

    /// 折扣徽标，同样直接使用 API 的 `cut` / Discount badge, again straight from the API's `cut`
    @ViewBuilder
    private var discountBadge: some View {
        if deal.deal.cut > 0 {
            Text("-\(deal.deal.cut)%")
                .font(.system(.footnote, design: .rounded, weight: .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 11)
                .padding(.vertical, 5)
                .background(Color.brandBlue, in: Capsule())
        }
    }

    /// 收藏按钮，叠在图片上靠阴影保证可读 / Favourite button drawn on the artwork, kept legible by a shadow
    private var favoriteButton: some View {
        Button(action: onToggleFavorite) {
            Image(systemName: isFavorite ? "heart.fill" : "heart")
                .font(.system(.body, weight: .semibold))
                .foregroundStyle(isFavorite ? Color.pink : Color.white)
                .shadow(color: .black.opacity(0.5), radius: 4, y: 1)
        }
        .buttonStyle(.plain)
        // 变暗而不是隐藏（HIG：不可用的项目保持可见）/ Dimmed rather than hidden (HIG: keep unavailable items visible)
        .disabled(!isFavoriteEnabled)
        .opacity(isFavoriteEnabled ? 1 : 0.4)
        .accessibilityLabel(isFavorite ? "Remove from favourites" : "Add to favourites")
    }

    // MARK: - 底部叠加 / Bottom overlay

    /// 标题在上、玻璃价格行在下，一起压住封面底部 /
    /// Title above the glass price row, both anchored to the bottom of the cover art
    private var bottomOverlay: some View {
        VStack(alignment: .leading, spacing: 0) {
            titleBlock
            priceRow
        }
    }

    /// 白字标题叠在封面上；不预留两行高度，短标题紧贴价格行，由 `overlay(alignment:)` 压底 /
    /// White title on the cover art; no reserved second line, and the overlay keeps it flush
    private var titleBlock: some View {
        Text(deal.title)
            .font(.system(.headline, weight: .bold))
            .foregroundStyle(.white)
            .lineLimit(2)
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.bottom, 10)
    }

    // MARK: - 价格行 / Price row

    /// Liquid Glass 价格行：iOS 26 的 `glassEffect`，`.clear` 比 `ultraThinMaterial` 更透 /
    /// Liquid Glass price row via the iOS 26 `glassEffect`; `.clear` beats `ultraThinMaterial`
    private var priceRow: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            currentPrice
            Spacer(minLength: 0)
            shopName
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        // `.clear` 最透；要磨砂感换 `.regular`，要压暗叠 `.tint(...)` / `.clear` is the most transparent of the variants
        .glassEffect(.clear, in: Rectangle())
    }

    /// 当前价，免费游戏显示 `Free`；长价格只缩字号不换行 /
    /// Current price, rendered as `Free` when there is nothing to pay; long values shrink instead of wrapping
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
        .font(.system(.title3, design: .monospaced, weight: .bold))
        .foregroundStyle(.brandGreen)
        .lineLimit(1)
        .minimumScaleFactor(0.7)
    }

    /// 购买商店名：`/deals/v2` 不返回评分，所以这个位置给商店；长名字缩字号不截断 /
    /// Shop name in the rating's slot; long names shrink instead of truncating
    private var shopName: some View {
        Text(deal.deal.shop.name)
            .font(.system(.subheadline, weight: .medium))
            .foregroundStyle(.secondaryText)
            .lineLimit(1)
            .minimumScaleFactor(0.65)
    }
}

#Preview {
    ScrollView {
        LazyVGrid(
            columns: [
                GridItem(.flexible(), spacing: 12),
                GridItem(.flexible(), spacing: 12),
            ],
            spacing: 12
        ) {
            DealGridCardView(deal: .previewSample, isFavorite: false, isFavoriteEnabled: true, onToggleFavorite: {})
            DealGridCardView(deal: .previewSample, isFavorite: true, isFavoriteEnabled: true, onToggleFavorite: {})
        }
        .padding(14)
    }
    .background(Color.appBackground)
}
