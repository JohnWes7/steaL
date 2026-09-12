//
//  GiveawayRowView.swift
//  steaL
//
//  Created by yijiang on 2026/9/11.
//

import SwiftUI

/// Giveaways 区块的通栏行：左侧满高缩略图 + 标题与商店，右侧为截止倒计时胶囊；
/// 整行就是一个按钮，点击后打开领取链接 /
/// Full-width row for the Giveaways section: full-height thumbnail on the left, title and shop
/// beside it, and a countdown capsule on the right; the whole row is one button that opens the claim link
struct GiveawayRowView: View {

    /// 要展示的限时免费领取 / The giveaway to show
    let giveaway: ITADGiveaway

    /// 用来打开外部领取页 / Used to open the external claim page
    @Environment(\.openURL) private var openURL

    /// 行圆角 / Row corner radius
    private let cornerRadius: CGFloat = 14

    /// 缩略图宽度，高度随行高拉满 / Thumbnail width; its height stretches to fill the row
    private let thumbnailWidth: CGFloat = 120

    var body: some View {
        Button {
            openClaimPage()
        } label: {
            row
        }
        .buttonStyle(GiveawayRowButtonStyle())
    }

    // MARK: - 行内容 / Row content

    /// 缩略图、文字与倒计时胶囊组成的一行 / One row of thumbnail, text and the countdown capsule
    private var row: some View {
        HStack(spacing: 12) {
            thumbnail

            textBlock

            Spacer(minLength: 8)

            deadlineChip
                .padding(.trailing, 12)
        }
        .background(Color.surfaceElevated, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .frame(height: 60)
    }

    /// 标题与商店名两行文字 / Title and shop in two lines
    private var textBlock: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(giveaway.displayTitle)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.primaryText)
                .lineLimit(2)

            Text(shopText)
                .font(.system(size: 13))
                .foregroundStyle(.secondaryText)
                .lineLimit(1)
        }
        .padding(.vertical, 12)
    }

    /// 缩略图。三种状态分开渲染，失败与加载中不再共用一个占位；左上角压一个 FREE 徽章 /
    /// Thumbnail with the three phases rendered separately, so loading and failure no longer share
    /// one placeholder; the FREE badge sits in its top-leading corner
    private var thumbnail: some View {
        AsyncImage(url: giveaway.thumbnailURL) { phase in
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
        .frame(width: thumbnailWidth)
        .frame(maxHeight: .infinity)
        .overlay(alignment: .topLeading) { freeBadge.padding(5) }
        .clipped()
    }

    /// 加载中占位，撑满缩略图区域 / Loading placeholder that fills the thumbnail area
    private var placeholder: some View {
        ZStack {
            Color.saleSurface
            Image(systemName: "photo")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.secondaryText)
        }
    }

    /// 加载失败占位，图标与加载中区分；以后要接重试就改在这一块 /
    /// Failure placeholder, told apart from the loading one by its icon; a retry affordance, when
    /// added, belongs here
    private var failedPlaceholder: some View {
        ZStack {
            Color.saleSurface
            Image(systemName: "photo.badge.exclamationmark")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.secondaryText)
        }
    }

    /// FREE 徽章，绿色胶囊呼应区块的绿色标识 / FREE badge, its green capsule echoing the section accent
    private var freeBadge: some View {
        Text("FREE")
            .font(.system(size: 10, weight: .heavy, design: .rounded))
            .foregroundStyle(Color.black.opacity(0.85))
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
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(isExpiringSoon ? Color.black.opacity(0.85) : Color.brandGreen)
                .lineLimit(1)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isExpiringSoon ? Color.brandGreen : Color.brandGreen.opacity(0.16), in: Capsule())
        }
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
