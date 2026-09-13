//
//  DealGridCardView.swift
//  steaL
//
//  Created by yijiang on 2026/9/11.
//

import SwiftUI

/// 网格里的小卡片：竖版 boxart 铺满整卡，折扣徽标与收藏在顶部，
/// 标题和玻璃价格行压在底部 /
/// Compact grid card: portrait box art fills the whole card, with the discount badge and
/// favourite toggle on top and the title plus a glass price row along the bottom
///
/// 与 `DealCardView` 的差异只有图片方向和字号：hero 用宽幅横幅 + 26pt 标题，
/// 这里用竖版封面 + 18pt 标题 /
/// Differs from `DealCardView` only in artwork orientation and type scale: the hero uses a
/// wide banner with a 26pt title, this uses portrait box art with an 18pt title
struct DealGridCardView: View {

    /// 要展示的游戏及其当前最佳优惠 / The game and its current best deal
    let deal: ITADDeal

    /// 是否已收藏 / Whether the user has favourited this game
    let isFavorite: Bool

    /// 点击收藏时的回调，状态同样由父视图持有 /
    /// Called when the favourite is tapped; state lives in the parent as well
    let onToggleFavorite: () -> Void

    /// 比 hero 卡略小的圆角 / Slightly tighter corner radius than the hero card
    private let cornerRadius: CGFloat = 16

    /// 竖版封面比例。2:3 对应 Steam 库封面与 ITAD boxart 的常见尺寸，
    /// 正好等于源图比例时 `scaledToFill` 不会有任何裁切 /
    /// Portrait ratio. 2:3 matches Steam library capsules and ITAD box art, so at this exact
    /// ratio `scaledToFill` crops nothing
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
            .overlay(alignment: .top) { topBar }
            .overlay(alignment: .bottom) { bottomOverlay }
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }

    /// 竖版封面。三种状态分开渲染，失败与加载中不再共用一个占位 /
    /// Portrait cover art, with the three phases rendered separately: loading and failure no
    /// longer share one placeholder
    @ViewBuilder
    private var boxArt: some View {
        AsyncImage(url: deal.portraitArtworkURL) { phase in
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

    /// 加载中占位 / Loading placeholder
    private var placeholder: some View {
        ZStack {
            Color.saleSurface
            Image(systemName: "photo")
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(.secondaryText)
        }
    }

    /// 加载失败占位，用不同图标与加载中区分。以后要接重试，就改在这一块 /
    /// Failure placeholder, told apart from the loading one by its icon. A retry affordance, when
    /// added, belongs here
    private var failedPlaceholder: some View {
        ZStack {
            Color.saleSurface
            Image(systemName: "photo.badge.exclamationmark")
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(.secondaryText)
        }
    }

    /// 底部压暗的渐变。除了保证白色标题可读，价格行的玻璃层也会采样到它 /
    /// Bottom-weighted scrim. Besides keeping the white title readable, the glass price row
    /// samples it as part of its backdrop
    private var scrim: some View {
        LinearGradient(
            stops: [
                .init(color: .black.opacity(0.45), location: 0),
                .init(color: .black.opacity(0), location: 0.22),
                .init(color: .black.opacity(0.10), location: 0.50),
                .init(color: .black.opacity(0.90), location: 1),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    /// 折扣徽标与收藏按钮。徽标颜色和位置与 hero 卡保持一致 /
    /// Discount badge and favourite button, matching the hero card's colour and corner
    private var topBar: some View {
        HStack(alignment: .top) {
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
                .font(.system(size: 13, weight: .bold, design: .rounded))
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
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(isFavorite ? Color.brandBlue : Color.white)
                .shadow(color: .black.opacity(0.5), radius: 4, y: 1)
        }
        .buttonStyle(.plain)
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

    /// 标题叠在封面上，所以用白色。这里不预留两行高度：短标题只占一行、紧贴价格行，
    /// 长标题最多折成两行后截断。因为整块由 `overlay(alignment: .bottom)` 压底，
    /// 所以无论几行都会贴着卡片底部 /
    /// The title sits on the cover art, hence white. It no longer reserves two lines: a short
    /// title takes a single line and hugs the price row, a longer one wraps to at most two lines
    /// before truncating. Since the whole block is pinned by `overlay(alignment: .bottom)`,
    /// either case stays flush with the card's bottom edge
    private var titleBlock: some View {
        Text(deal.title)
            .font(.system(size: 18, weight: .bold))
            .foregroundStyle(.white)
            .lineLimit(2)
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.bottom, 10)
    }

    // MARK: - 价格行 / Price row

    /// Liquid Glass 价格行：整条改用 iOS 26 的 `glassEffect` 渲染，`.clear` 变体比
    /// `ultraThinMaterial` 更透，还会随背景亮度自适应高光 /
    /// Liquid Glass price row, rendered with the iOS 26 `glassEffect`. The `.clear` variant is
    /// more transparent than `ultraThinMaterial` and adapts its highlight to the backdrop
    private var priceRow: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            currentPrice
            Spacer(minLength: 0)
            shopName
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        // 先看最透的 `.clear`：想要常规磨砂感改回 `.regular`，想压暗就叠 `.tint(...)` /
        // Start with the most transparent `.clear`: switch back to `.regular` for the classic
        // frosted look, or chain `.tint(...)` for a darker strip
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
        .font(.system(size: 19, weight: .bold, design: .monospaced))
        .foregroundStyle(.brandGreen)
        .lineLimit(1)
        .minimumScaleFactor(0.7)
    }

    /// 购买商店。参考图这里原本是评分，但 `/deals/v2` 不返回评分，改为显示商店名。
    /// 栏位较窄，`MacGameStore` 这类长名字靠缩字号而不是截断来容纳 /
    /// Shop name. The mockup had a rating here, but `/deals/v2` has none, so the shop takes its
    /// place. The slot is narrow, so long names like `MacGameStore` shrink instead of truncating
    private var shopName: some View {
        Text(deal.deal.shop.name)
            .font(.system(size: 14, weight: .medium))
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
            DealGridCardView(deal: .previewSample, isFavorite: false, onToggleFavorite: {})
            DealGridCardView(deal: .previewSample, isFavorite: true, onToggleFavorite: {})
        }
        .padding(14)
    }
    .background(Color.appBackground)
}
