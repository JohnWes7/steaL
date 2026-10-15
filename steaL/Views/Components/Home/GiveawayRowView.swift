//
//  GiveawayRowView.swift
//  steaL
//
//  Created by yijiang on 2026/9/11.
//

import SwiftUI

/// Giveaways 区块的通栏行：左侧满高缩略图 + 标题与商店，右侧倒计时胶囊 /
/// Full-width row for the Giveaways section: full-height thumbnail, title and shop, countdown capsule
///
/// 整行就是一个按钮，点击打开领取链接 / The whole row is one button that opens the claim link
struct GiveawayRowView: View {

    /// 要展示的限时免费领取 / The giveaway to show
    let giveaway: ITADGiveaway

    /// 用来打开外部领取页 / Used to open the external claim page
    @Environment(\.openURL) private var openURL

    /// 行圆角 / Row corner radius
    private let cornerRadius: CGFloat = 14

    /// 缩略图宽度，跟着标题字号一起放大。
    /// Thumbnail width, scaled along with the title's text size.
    @ScaledMetric(relativeTo: .callout) private var thumbnailWidth: CGFloat = 120

    /// 缩略图的宽高比，同 ITAD `banner300` / `banner400` 与 Steam header 的 460:215。
    /// The thumbnail's aspect ratio, matching ITAD `banner300` / `banner400` and the Steam header at 460:215.
    private let thumbnailAspectRatio: CGFloat = 460.0 / 215.0

    /// 当前的文字大小，辅助功能档位下换成上图下文。
    /// The current text size; accessibility sizes switch to the image above the text.
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        Button {
            openClaimPage()
        } label: {
            row
        }
        .buttonStyle(GiveawayRowButtonStyle())
        .accessibilityLabel(accessibilitySummary)
    }

    // MARK: - 行内容 / Row content

    /// 一整行：内容按文字大小换布局，底色与圆角两种布局共用。
    /// The whole row: its content changes layout with the text size, sharing one background and corner radius.
    private var row: some View {
        rowContent
            .background(Color.surfaceElevated, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }

    /// 平时左图右文，辅助功能档位下上图下文。
    /// Image beside the text normally, image above the text at accessibility sizes.
    ///
    /// 辅助功能档位下缩略图跟着放大会宽到放不下文字，所以改成竖排。
    /// At accessibility sizes the scaled thumbnail leaves no room for the text, so the layout stacks.
    @ViewBuilder
    private var rowContent: some View {
        if dynamicTypeSize.isAccessibilitySize {
            stackedRow
        } else {
            sideBySideRow
        }
    }

    /// 左侧缩略图撑满行高，右侧标题、商店与倒计时胶囊。
    /// Thumbnail filling the row height on the left, with the title, shop and countdown capsule on the right.
    ///
    /// 缩略图放在 `background` 里，只填满文字撑出来的高度，不参与决定行高；否则加载中、竖版封面这些不同比例会让行高跳动。
    /// 行高至少是缩略图按原比例的高度，标题折成两行时图片拉高、左右裁掉一点。
    /// The thumbnail lives in the `background` so it only fills the height the text sets and never decides it;
    /// otherwise loading placeholders and portrait fallbacks would make the row height jump.
    /// The row is at least as tall as the thumbnail at its own ratio; a two-line title makes the image taller and crops its sides.
    private var sideBySideRow: some View {
        HStack(spacing: 12) {
            textBlock
                .padding(.vertical, 12)

            Spacer(minLength: 8)

            deadlineChip
                .padding(.trailing, 12)
        }
        // 给左侧缩略图留出宽度与间距 / Leaves room for the thumbnail and its gap on the left
        .padding(.leading, thumbnailWidth + 12)
        .frame(minHeight: thumbnailHeight)
        .background(alignment: .leading) {
            sideThumbnail
        }
    }

    /// 缩略图整宽在上，标题、商店与倒计时胶囊在下。
    /// Full-width thumbnail on top, with the title, shop and countdown capsule below.
    private var stackedRow: some View {
        VStack(alignment: .leading, spacing: 0) {
            topThumbnail

            VStack(alignment: .leading, spacing: 10) {
                textBlock
                deadlineChip
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
        }
    }

    /// 标题与商店名两行文字 / Title and shop in two lines
    private var textBlock: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(giveaway.displayTitle)
                .font(.system(.callout, weight: .semibold))
                .foregroundStyle(.primaryText)
                .lineLimit(titleLineLimit)

            Text(shopText)
                .font(.footnote)
                .foregroundStyle(.secondaryText)
                .lineLimit(1)
        }
    }

    /// 左侧缩略图：宽度固定，高度填满整行，左上角压 FREE 徽章。
    /// Side thumbnail: fixed width, filling the row height, with the FREE badge in its top-leading corner.
    private var sideThumbnail: some View {
        thumbnailImage
            .frame(width: thumbnailWidth)
            .frame(maxHeight: .infinity)
            .overlay(alignment: .topLeading) { freeBadge.padding(5) }
            .clipped()
    }

    /// 顶部缩略图：整宽，按 460:215 完整显示，左上角压 FREE 徽章。
    /// Top thumbnail: full width, shown whole at 460:215, with the FREE badge in its top-leading corner.
    private var topThumbnail: some View {
        Color.clear
            .aspectRatio(thumbnailAspectRatio, contentMode: .fit)
            .overlay { thumbnailImage }
            .overlay(alignment: .topLeading) { freeBadge.padding(8) }
            .clipped()
    }

    /// 缩略图图片。三种状态分开渲染，失败与加载中不再共用一个占位。
    /// The thumbnail image, with the three phases rendered separately so loading and failure no longer share one placeholder.
    private var thumbnailImage: some View {
        RetryingAsyncImage(url: giveaway.thumbnailURL) { phase in
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

    /// 加载中占位：占位底色上一个系统转圈，撑满缩略图区域。
    /// Loading placeholder: a system spinner on the placeholder colour, filling the thumbnail area.
    ///
    /// 与失败的图标区分开，一眼能看出还在加载。
    /// It sets loading apart from the failure icon, so an unfinished load reads as one at a glance.
    private var placeholder: some View {
        ZStack {
            Color.imagePlaceholder
            ProgressView()
                .controlSize(.small)
        }
    }

    /// 加载失败占位，图标与加载中区分；以后要接重试就改在这一块 /
    /// Failure placeholder, told apart from the loading one by its icon; a retry affordance, when
    /// added, belongs here
    private var failedPlaceholder: some View {
        ZStack {
            Color.imagePlaceholder
            Image(systemName: "photo.badge.exclamationmark")
                .font(.system(.body, weight: .semibold))
                .foregroundStyle(.secondaryText)
        }
    }

    /// FREE 徽章，绿色胶囊呼应区块的绿色标识 / FREE badge, its green capsule echoing the section accent
    private var freeBadge: some View {
        Text("FREE")
            .font(.system(.caption2, design: .rounded, weight: .heavy))
            .foregroundStyle(Color.onBrandGreen)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(Color.brandGreen, in: Capsule())
    }

    /// 截止倒计时胶囊，替代原来的 Get 提示；无截止时间时整块隐藏 /
    /// Deadline countdown capsule replacing the old "Get" cue; hidden when there is no deadline
    @ViewBuilder
    private var deadlineChip: some View {
        if let expiryText {
            Text(expiryText)
                .font(.system(.footnote, weight: .semibold))
                .foregroundStyle(isExpiringSoon ? Color.onBrandGreen : Color.brandGreen)
                .lineLimit(1)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isExpiringSoon ? Color.brandGreen : Color.brandGreen.opacity(0.16), in: Capsule())
        }
    }

    // MARK: - 尺寸 / Sizes

    /// 缩略图按 460:215 完整显示时的高度，也是左图右文时的最小行高。
    /// The thumbnail's height when shown whole at 460:215, which is also the minimum row height side by side.
    private var thumbnailHeight: CGFloat {
        thumbnailWidth / thumbnailAspectRatio
    }

    /// 标题的行数上限：平时两行，辅助功能档位下不限。
    /// The title's line limit: two lines normally, unlimited at accessibility sizes.
    private var titleLineLimit: Int? {
        if dynamicTypeSize.isAccessibilitySize {
            return nil
        }
        return 2
    }

    // MARK: - 文案 / Copy

    /// 商店名，`shop` 为 nil 时用中性说法 / Shop name, with a neutral label when `shop` is nil
    private var shopText: String {
        giveaway.shop?.name ?? "Redeem code"
    }

    /// 截止倒计时：一周内显示剩余天数，更远显示具体日期，缺失时不显示 /
    /// Countdown copy: days left within a week, an exact date beyond that, nothing when missing
    private var expiryText: String? {
        guard let expiryDate = giveaway.expiryDate else { return nil }

        let days = Calendar.current.dateComponents([.day], from: .now, to: expiryDate).day ?? 0
        if days <= 0 { return "Today" }
        if days <= 7 { return "\(days)d left" }
        return expiryDate.formatted(.dateTime.month(.abbreviated).day())
    }

    /// 是否不满 48 小时截止，用来给倒计时上强调色 /
    /// True when the deadline is within 48 hours, used to emphasise the countdown
    private var isExpiringSoon: Bool {
        guard let expiryDate = giveaway.expiryDate else { return false }
        return expiryDate.timeIntervalSinceNow <= 48 * 60 * 60
    }

    // MARK: - 无障碍 / Accessibility

    /// VoiceOver 读的整行：免费、标题、商店、截止时间。
    /// What VoiceOver reads for the row: free, title, shop and deadline.
    ///
    /// 界面上的「3d left」缩写读出来是「3 d left」，这里换成完整说法。
    /// The on-screen "3d left" is read as "3 d left", so the deadline is spelled out here.
    private var accessibilitySummary: String {
        var parts: [String] = ["Free", giveaway.displayTitle, shopText]

        if let spokenExpiryText {
            parts.append(spokenExpiryText)
        }

        return parts.joined(separator: ", ")
    }

    /// 读给 VoiceOver 的截止时间，规则同 `expiryText`，缩写换成完整说法。
    /// The deadline as VoiceOver reads it: the same rules as `expiryText`, with the abbreviations spelled out.
    private var spokenExpiryText: String? {
        guard let expiryDate = giveaway.expiryDate else { return nil }

        let days = Calendar.current.dateComponents([.day], from: .now, to: expiryDate).day ?? 0
        if days <= 0 { return "ends today" }
        if days == 1 { return "1 day left" }
        if days <= 7 { return "\(days) days left" }

        let dateText = expiryDate.formatted(.dateTime.month(.wide).day())
        return "ends \(dateText)"
    }

    // MARK: - 操作 / Actions

    /// 打开领取链接；URL 无法解析时整行保持无动作 / Opens the claim link; the row stays inert when the URL cannot be parsed
    private func openClaimPage() {
        guard let url = URL(string: giveaway.url) else { return }
        openURL(url)
    }
}

/// 整行按压时的轻微缩放与变暗反馈 / Slight scale and dim while the whole row is pressed
private struct GiveawayRowButtonStyle: ButtonStyle {

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.985 : 1)
            .opacity(configuration.isPressed ? 0.9 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

#Preview {
    ScrollView {
        VStack(spacing: 10) {
            GiveawayRowView(giveaway: .previewSample)
            GiveawayRowView(giveaway: .previewSampleFallback)
        }
        .padding(14)
    }
    .background(Color.appBackground)
}

#Preview("大字号 / Accessibility size") {
    ScrollView {
        VStack(spacing: 10) {
            GiveawayRowView(giveaway: .previewSample)
            GiveawayRowView(giveaway: .previewSampleFallback)
        }
        .padding(14)
    }
    .background(Color.appBackground)
    .dynamicTypeSize(.accessibility3)
}
