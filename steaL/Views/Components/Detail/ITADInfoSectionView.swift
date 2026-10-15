//
//  ITADInfoSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// ITAD 评分与热度 / ITAD reviews and popularity
struct ITADInfoSectionView: View {

    /// 信息区块的状态 / The state of the info section
    let state: GameDetailViewModel.InfoState

    /// 重试动作 / The retry action
    let retry: () -> Void

    var body: some View {
        // `.hidden` 连标题一起不渲染 / `.hidden` takes the title down with it
        if state != .hidden {
            section
        }
    }

    /// 区块本体 / The section itself
    private var section: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("ITAD DETAILS")
                .font(.system(.footnote, weight: .bold))
                .foregroundStyle(.brandGreen)
                .accessibilityAddTraits(.isHeader)

            switch state {
            case .loading:
                ProgressView()
                    .controlSize(.small)

            case .loaded(let info):
                let reviews =
                    info.reviews?.filter {
                        $0.source?.isEmpty == false && $0.score != nil
                    } ?? []

                if !reviews.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Reviews")
                            .font(.system(.title2, weight: .heavy))
                            .foregroundStyle(.primaryText)

                        ForEach(Array(reviews.enumerated()), id: \.offset) { _, review in
                            reviewRow(review)
                        }
                    }
                }

                let recentPlayers = info.players?.recent.flatMap { $0 > 0 ? $0 : nil }
                let itadRank = info.stats?.rank.flatMap { $0 > 0 ? $0 : nil }

                if recentPlayers != nil || itadRank != nil {
                    HStack(spacing: 0) {
                        if let recentPlayers {
                            itadMetric(
                                icon: "person.3.fill",
                                label: "Players online",
                                value: recentPlayers.formatted(),
                                tint: .brandGreen
                            )
                        }

                        // `recentPlayers` 与 `itadRank` 都有值时加竖线分隔，避免视觉上挤在一起 / When both `recentPlayers` and `itadRank` are available, add a vertical separator to avoid visual crowding
                        if recentPlayers != nil && itadRank != nil {
                            Rectangle()
                                .fill(Color.secondaryText.opacity(0.24))
                                .frame(width: 1, height: 58)
                                .padding(.horizontal, 16)
                        }

                        if let itadRank {
                            itadMetric(
                                icon: "medal.fill",
                                label: "ITAD rank",
                                value: "#\(itadRank)",
                                tint: .brandBlue
                            )
                        }
                    }
                }

            // 查了没有：不给 Retry，重试也不会变 / A plain miss: no retry, since it would not change anything
            case .notFound:
                UnavailablePromptView(message: "No IsThereAnyDeal details for this game.")

            case .failed:
                UnavailablePromptView(message: "Couldn't load details.", retry: retry)

            // `body` 已经挡掉 `.hidden` / `body` filters `.hidden` out
            case .hidden:
                EmptyView()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// ITAD 底部指标：图标、重点数字与简短标签 /
    /// Compact ITAD metric with an icon, a prominent value, and a short label
    private func itadMetric(icon: String, label: String, value: String, tint: Color) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(.subheadline, weight: .bold))
                .foregroundStyle(.appBackground)
                .frame(width: 32, height: 32)
                .background(tint, in: RoundedRectangle(cornerRadius: 9, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(value)
                    .font(.system(.title3, design: .rounded, weight: .heavy))
                    .foregroundStyle(tint)

                Text(label)
                    .font(.system(.caption, design: .rounded, weight: .medium))
                    .foregroundStyle(.secondaryText)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label), \(value)")
    }

    /// 一个评分来源：分数、评分人数与视觉比例条 / A review source with score, count, and a visual ratio bar
    private func reviewRow(_ review: ITADReview) -> some View {
        let score = min(max(review.score ?? 0, 0), 100)
        let source = review.source ?? "Unknown"

        return VStack(alignment: .leading, spacing: 9) {
            HStack(alignment: .firstTextBaseline) {
                HStack(spacing: 6) {
                    Image(systemName: "hand.thumbsup.fill")
                        .font(.system(.subheadline, weight: .bold))
                        .foregroundStyle(.brandGreen)

                    Text("\(score)%")
                        .font(.system(.body, design: .rounded, weight: .bold))
                        .foregroundStyle(.brandGreen)
                }

                Spacer(minLength: 12)

                Text(review.count.map { "\($0.formatted()) reviews" } ?? "Review count unavailable")
                    .font(.system(.subheadline, design: .rounded, weight: .medium))
                    .foregroundStyle(.secondaryText)
            }

            GeometryReader { proxy in
                HStack(spacing: 3) {
                    Capsule()
                        .fill(Color.brandGreen)
                        .frame(width: proxy.size.width * CGFloat(score) / 100)

                    Capsule()
                        .fill(Color.red.opacity(0.75))
                }
            }
            .frame(height: 7)

            HStack {
                Text(source)
                    .font(.system(.body, design: .rounded, weight: .semibold))
                    .foregroundStyle(.primaryText)

                Spacer(minLength: 12)

                Text("\(score)/100")
                    .font(.system(.subheadline, design: .rounded, weight: .medium))
                    .foregroundStyle(.secondaryText)
            }
        }
        .padding(.vertical, 12)
        .overlay(alignment: .bottom) {
            Divider()
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "\(source), \(score) percent, \(review.count.map { $0.formatted() } ?? "review count unavailable") reviews"
        )
    }
}

#Preview("有评分与热度 / With reviews") {
    ScrollView {
        ITADInfoSectionView(state: .loaded(.previewSample), retry: {})
            .padding(20)
    }
    .background(Color.appBackground)
}

#Preview("其他状态 / Other states") {
    ScrollView {
        VStack(alignment: .leading, spacing: 24) {
            ITADInfoSectionView(state: .loading, retry: {})
            ITADInfoSectionView(state: .notFound, retry: {})
            ITADInfoSectionView(state: .failed, retry: {})
        }
        .padding(20)
    }
    .background(Color.appBackground)
}
