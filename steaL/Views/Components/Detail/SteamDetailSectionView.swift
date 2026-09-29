//
//  SteamDetailSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// Steam 简介、开发商与发行商的折叠区：平铺在页面上，不用卡片包裹 /
/// Collapsible Steam description and credits, laid out flat rather than inside a card
///
/// 收起时露三行、`More ⌄` 贴最后一行右端，没截断时退到文字下方 / Collapsed shows three lines
struct SteamDetailSectionView: View {

    /// Steam 详情 / The Steam details
    let details: SteamAppDetails

    /// 是否展开；状态由详情页持有，重试后不会丢 /
    /// Whether the section is expanded; the page owns the state so a retry keeps it
    @Binding var isExpanded: Bool

    /// 用户开了「减弱动态效果」时跳过展开动画 /
    /// Skips the expand animation when Reduce Motion is turned on
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let developers = details.developers?.filter { !$0.isEmpty } ?? []
        let publishers = details.publishers?.filter { !$0.isEmpty } ?? []
        let categories = details.categories?.compactMap(\.description).filter { !$0.isEmpty } ?? []
        let platforms = details.platformText
        let hasPlatforms = !platforms.isEmpty && platforms != "—"

        if details.shortDescription != nil
            || !developers.isEmpty
            || !publishers.isEmpty
            || !categories.isEmpty
            || hasPlatforms
        {
            VStack(alignment: .leading, spacing: 14) {
                Text("GAME DETAIL")
                    .font(.system(.footnote, weight: .bold))
                    .foregroundStyle(.brandGreen)

                if isExpanded {
                    if let description = details.shortDescription {
                        descriptionText(description)
                    }

                    creditsList(developers: developers, publishers: publishers)

                    steamMetadataList(
                        categories: categories,
                        platforms: hasPlatforms ? platforms : nil
                    )

                    HStack(spacing: 0) {
                        Spacer(minLength: 0)

                        Button {
                            toggleSteamDetail()
                        } label: {
                            MoreToggleLabel(isExpanded: isExpanded)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Show less")
                    }
                } else {
                    if let description = details.shortDescription {
                        descriptionText(description, lineLimit: 3)
                            .overlay(alignment: .bottomTrailing) {
                                inlineMoreButton
                            }
                    } else {
                        HStack(spacing: 0) {
                            Spacer(minLength: 0)
                            inlineMoreButton
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// 贴在简介最后一行右端的展开开关，左侧渐变让底下的文字自然淡出，避免生硬的截断 /
    /// The expand control riding the end of the description's last line; the gradient fades the text
    /// out underneath instead of cutting it off
    private var inlineMoreButton: some View {
        Button {
            toggleSteamDetail()
        } label: {
            MoreToggleLabel(isExpanded: isExpanded)
                .padding(.leading, 36)
                .padding(.vertical, 3)
                .background(
                    LinearGradient(
                        stops: [
                            .init(color: .appBackground.opacity(0), location: 0),
                            .init(color: .appBackground, location: 0.35),
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Show more")
    }

    /// 简介正文的统一样式 / Shared styling for the description body
    private func descriptionText(_ text: String, lineLimit: Int? = nil) -> some View {
        Text(text)
            .font(.system(.body, design: .rounded, weight: .regular))
            .foregroundStyle(.secondaryText)
            .lineSpacing(5)
            .lineLimit(lineLimit)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// 厂商信息，展开后才出现 / The credits, shown once the section is expanded
    @ViewBuilder
    private func creditsList(developers: [String], publishers: [String]) -> some View {
        if !developers.isEmpty {
            infoRow(label: "Developer", value: developers.joined(separator: ", "))
        }
        if !publishers.isEmpty {
            infoRow(label: "Publisher", value: publishers.joined(separator: ", "))
        }
    }

    /// Steam 分类与支持平台 / Steam categories and supported platforms
    @ViewBuilder
    private func steamMetadataList(
        categories: [String],
        platforms: String?
    ) -> some View {
        if !categories.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("Categories")
                    .font(.system(.subheadline, weight: .semibold))
                    .foregroundStyle(.secondaryText)

                categoryChipGrid(categories)
            }
        }
        if let platforms {
            infoRow(label: "Platforms", value: platforms)
        }
    }

    /// Detail 内的分类标签自动换行并完整显示 / Detail categories wrap and remain fully visible
    private func categoryChipGrid(_ labels: [String]) -> some View {
        FlowLayout(itemSpacing: 8, rowSpacing: 8) {
            ForEach(Array(labels.enumerated()), id: \.offset) { _, label in
                Text(label)
                    .font(.system(.subheadline, design: .rounded, weight: .semibold))
                    .foregroundStyle(.primaryText)
                    .padding(.horizontal, 12)
                    .frame(minHeight: 32)
                    .background(Color.surfaceElevated, in: Capsule())
            }
        }
    }

    /// 一行信息 / One info row
    private func infoRow(label: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(label)
                .font(.system(.subheadline, weight: .semibold))
                .foregroundStyle(.secondaryText)
                .frame(width: 96, alignment: .leading)

            Text(value)
                .font(.system(.subheadline, weight: .medium))
                .foregroundStyle(.primaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// 展开与收起；开了减弱动态效果就直接切换，不带动画 /
    /// Toggles the section; with Reduce Motion on it switches without the transition
    private func toggleSteamDetail() {
        if reduceMotion {
            isExpanded.toggle()
        } else {
            withAnimation(.snappy(duration: 0.3)) {
                isExpanded.toggle()
            }
        }
    }
}

/// 按子项真实宽度排列并自动换行的流式布局 /
/// A wrapping layout that uses each item's intrinsic width
private struct FlowLayout: Layout {
    let itemSpacing: CGFloat
    let rowSpacing: CGFloat

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        let rows = makeRows(width: proposal.width, subviews: subviews)
        let height =
            rows.reduce(0) { total, row in
                total + row.height
            } + CGFloat(max(0, rows.count - 1)) * rowSpacing

        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: height)
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let rows = makeRows(width: bounds.width, subviews: subviews)
        var y = bounds.minY

        for row in rows {
            var x = bounds.minX

            for index in row.indices {
                let subview = subviews[index]
                let size = subview.sizeThatFits(.unspecified)
                subview.place(
                    at: CGPoint(x: x, y: y),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(size)
                )
                x += size.width + itemSpacing
            }

            y += row.height + rowSpacing
        }
    }

    private func makeRows(width: CGFloat?, subviews: Subviews) -> [FlowRow] {
        let availableWidth = width ?? .infinity
        var rows: [FlowRow] = []
        var current = FlowRow()

        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let proposedWidth =
                current.width + (current.indices.isEmpty ? 0 : itemSpacing) + size.width

            if !current.indices.isEmpty && proposedWidth > availableWidth {
                rows.append(current)
                current = FlowRow()
            }

            current.indices.append(index)
            current.width += (current.indices.count == 1 ? 0 : itemSpacing) + size.width
            current.height = max(current.height, size.height)
        }

        if !current.indices.isEmpty {
            rows.append(current)
        }

        return rows
    }

    private struct FlowRow {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }
}

#Preview("收起 / Collapsed") {
    @Previewable @State var isExpanded = false

    ScrollView {
        SteamDetailSectionView(details: .previewSample, isExpanded: $isExpanded)
            .padding(20)
    }
    .background(Color.appBackground)
}

#Preview("展开 / Expanded") {
    @Previewable @State var isExpanded = true

    ScrollView {
        SteamDetailSectionView(details: .previewSample, isExpanded: $isExpanded)
            .padding(20)
    }
    .background(Color.appBackground)
}
