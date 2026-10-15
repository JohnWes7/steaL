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

    /// 能否收藏，没有 Steam appID 时变暗禁用 / Whether it can be favourited; dimmed and disabled without a Steam appID
    let isFavoriteEnabled: Bool

    /// 点击收藏时的回调。状态由父视图持有，避免 `LazyVStack` 回收卡片时丢失 /
    /// Called when the favourite button is tapped; state lives in the parent so `LazyVStack` recycling cannot drop it
    let onToggleFavorite: () -> Void

    /// 卡片圆角，图片区和价格行共用同一套裁切 / Shared corner radius, clipped once for the whole card
    private let cornerRadius: CGFloat = 20

    /// 当前的文字大小，辅助功能档位下换成竖排布局。
    /// The current text size; accessibility sizes switch to a stacked layout.
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        VStack(spacing: 0) {
            artwork
            priceRow
        }
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        // 整张卡读成一句，心形合并后成为卡片的自定义操作 / The card reads as one sentence; once combined, the heart becomes a custom action
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilitySummary)
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
            .overlay(alignment: .bottom) {
                // 辅助功能档位下标题放不进图片，改到价格行里 / At accessibility sizes the title moves into the price row
                if !dynamicTypeSize.isAccessibilitySize {
                    titleBlock
                }
            }
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

    /// 加载中占位：占位底色上一个系统转圈，撑满图片区。
    /// Loading placeholder: a system spinner on the placeholder colour, filling the artwork area.
    ///
    /// 与失败的图标区分开，一眼能看出还在加载。
    /// It sets loading apart from the failure icon, so an unfinished load reads as one at a glance.
    private var placeholder: some View {
        ZStack {
            Color.imagePlaceholder
            ProgressView()
        }
    }

    /// 加载失败占位，用不同图标与加载中区分；重试由外层 `RetryingAsyncImage` 负责 /
    /// Failure placeholder, told apart from the loading one by its icon; `RetryingAsyncImage` retries
    private var failedPlaceholder: some View {
        ZStack {
            Color.imagePlaceholder
            Image(systemName: "photo.badge.exclamationmark")
                .font(.system(.largeTitle, weight: .semibold))
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
        HStack {
            discountBadge
            Spacer(minLength: 0)
            favoriteButton
        }
        // 心形的 44 pt 可点框比图标大一圈，右、上少留边，看得见的位置和原来一样 /
        // The heart's 44 pt hit frame is larger than the glyph, so less inset on the trailing and top edges keeps it where it was
        .padding(.leading, 14)
        .padding(.trailing, 5)
        .padding(.top, 5)
    }

    /// 折扣徽标。直接使用 API 的 `cut`，不做本地重算，避免与服务端舍入不一致 /
    /// Discount badge using the API's `cut` as-is; recomputing locally risks disagreeing with the server
    @ViewBuilder
    private var discountBadge: some View {
        if deal.deal.cut > 0 {
            Text("-\(deal.deal.cut)%")
                .font(.system(.subheadline, design: .rounded, weight: .bold))
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
                .font(.system(.title2, weight: .semibold))
                .foregroundStyle(isFavorite ? Color.pink : Color.white)
                .shadow(color: .black.opacity(0.45), radius: 5, y: 1)
                // 图标只有约 26 pt，可点范围撑到 44 pt，免得点偏进了详情页 /
                // The glyph is only about 26 pt; widen the hit area to 44 pt so a near-miss doesn't open the detail page
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(.rect)
        }
        .buttonStyle(.plain)
        // 变暗而不是隐藏（HIG：不可用的项目保持可见）/ Dimmed rather than hidden (HIG: keep unavailable items visible)
        .disabled(!isFavoriteEnabled)
        .opacity(isFavoriteEnabled ? 1 : 0.4)
        .accessibilityLabel(isFavorite ? "Remove from Wishlist" : "Add to Wishlist")
    }

    /// 图片底部的质量标签与标题 / Quality tag and title pinned over the bottom of the artwork
    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            // 压在暗色渐变上，固定用 dark 取色 / Sits on the dark scrim, so it resolves dark colours
            qualityTag
                .environment(\.colorScheme, .dark)

            titleText
                .foregroundStyle(.white)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
    }

    /// 绿色质量标签，没有标签时不显示。
    /// The green quality tag, hidden when there is no label.
    @ViewBuilder
    private var qualityTag: some View {
        if let qualityLabel {
            Text(qualityLabel)
                .font(.system(.caption, weight: .heavy))
                .tracking(0.6)
                .foregroundStyle(.brandGreen)
        }
    }

    /// 游戏标题，颜色由放置的位置决定。
    /// The game title; where it sits decides its colour.
    private var titleText: some View {
        Text(deal.title)
            .font(.system(.title, weight: .bold))
            .multilineTextAlignment(.leading)
    }

    // MARK: - 价格行 / Price row

    /// 底部价格行，内容按文字大小换布局。
    /// Bottom price row, whose content changes layout with the text size.
    private var priceRow: some View {
        priceRowContent
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
    }

    /// 价格行的内容：平时左价格右商店；辅助功能档位下标题、价格、商店从上到下排。
    /// Price row content: prices left and shop right normally; title, prices and shop stacked at accessibility sizes.
    ///
    /// 大字号下横排放不下，标题也盖满图片，所以换成竖排而不是缩小或截断文字。
    /// At large sizes the row no longer fits and the title would cover the artwork, so the layout stacks instead of shrinking or truncating text.
    @ViewBuilder
    private var priceRowContent: some View {
        if dynamicTypeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    qualityTag
                    titleText
                        .foregroundStyle(.primaryText)
                }

                prices
                shopPill
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            HStack(alignment: .center, spacing: 12) {
                prices
                Spacer(minLength: 0)
                shopPill
            }
        }
    }

    /// 当前价在上、划线原价在下。
    /// Current price above the struck-through regular price.
    private var prices: some View {
        VStack(alignment: .leading, spacing: 1) {
            currentPrice
            regularPrice
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
        .font(.system(.title, design: .monospaced, weight: .bold))
        .foregroundStyle(.brandGreen)
    }

    /// 划线原价，仅在确实低于原价时显示 / Struck-through regular price, only shown when it is actually higher
    @ViewBuilder
    private var regularPrice: some View {
        let regular = deal.deal.regular

        if regular.amountInt > deal.deal.price.amountInt {
            Text(regular.amount, format: .currency(code: regular.currency))
                .font(.system(.callout, design: .monospaced, weight: .medium))
                .foregroundStyle(.secondaryText)
                .strikethrough()
        }
    }

    /// 购买商店标签。`/deals/v2` 每个游戏只返回一个最佳价格，因此这里恒为一个商店 /
    /// Shop tag. `/deals/v2` returns a single best deal per game, so there is only ever one shop here
    private var shopPill: some View {
        Text(deal.deal.shop.name)
            .font(.system(.subheadline, weight: .semibold))
            .foregroundStyle(.secondaryText)
            .lineLimit(1)
            .padding(.horizontal, 13)
            .padding(.vertical, 8)
            .background(.fill.tertiary, in: Capsule())
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

    /// VoiceOver 读的整张卡：标题、质量标签、折扣、现价、原价、商店。
    /// What VoiceOver reads for the card: title, quality tag, discount, price, regular price and shop.
    ///
    /// 标题放最前面，滑过卡片时先听到是哪个游戏；折扣写成「75% off」，不按徽标读成「minus 75 percent」。
    /// The title comes first so a swipe tells you which game it is; the discount reads "75% off" rather than the badge's "minus 75 percent".
    private var accessibilitySummary: String {
        let info = deal.deal
        var parts: [String] = [deal.title]

        if let qualityLabel {
            parts.append(qualityLabel.capitalized)
        }

        if info.cut > 0 {
            parts.append("\(info.cut)% off")
        }

        parts.append(info.price.spokenText)

        // 与界面一致：只有原价确实更高才读 / Matches the screen: only read when the regular price is actually higher
        if info.regular.amountInt > info.price.amountInt {
            parts.append("was \(info.regular.spokenText)")
        }

        parts.append(info.shop.name)
        return parts.joined(separator: ", ")
    }
}

#Preview {
    ScrollView {
        DealCardView(deal: .previewSample, isFavorite: false, isFavoriteEnabled: true, onToggleFavorite: {})
            .padding(14)
    }
    .background(Color.appBackground)
}

#Preview("大字号 / Accessibility size") {
    ScrollView {
        DealCardView(deal: .previewSample, isFavorite: true, isFavoriteEnabled: true, onToggleFavorite: {})
            .padding(14)
    }
    .background(Color.appBackground)
    .dynamicTypeSize(.accessibility3)
}
