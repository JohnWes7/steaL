//
//  PriceSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 历史价格窗口与当前报价 /
/// The historical price windows and the current offers
///
/// 比价晚于详情到达，所以它自己带一个小加载态，不让整页回到转圈 /
/// The prices arrive after the details, so the section carries its own little loading state
/// instead of sending the whole page back to a spinner
struct PriceSectionView: View {

    /// 比价区块的状态 / The state of the price section
    let state: GameDetailViewModel.PricesState

    /// 用户选的报价筛选；状态由详情页持有，重试后不会丢 /
    /// The offer filter chosen by the user; the page owns the state so a retry keeps it
    @Binding var offerFilter: OfferFilter

    /// 重试动作 / The retry action
    let retry: () -> Void

    /// 用来打开外部商店页 / Used to open the external store page
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("PRICES")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.brandGreen)

            switch state {
            case .idle:
                EmptyView()

            case .loading:
                ProgressView()
                    .controlSize(.small)

            case .free:
                Text("Free")
                    .font(.system(size: 22, weight: .heavy, design: .rounded))
                    .foregroundStyle(.brandGreen)

            case .demo:
                Text("Demo")
                    .font(.system(size: 22, weight: .heavy, design: .rounded))
                    .foregroundStyle(.brandGreen)

            case .comingSoon:
                Text("Not released yet")
                    .font(.system(size: 14, weight: .regular, design: .rounded))
                    .foregroundStyle(.secondaryText)

            case .loaded(let prices):
                let lows = [
                    ("All-time low", prices.historyLow.all),
                    ("1 year low", prices.historyLow.y1),
                    ("3 months low", prices.historyLow.m3),
                ].compactMap { label, price in
                    price.map { (label, $0) }
                }

                if lows.isEmpty {
                    Text("No historical price data")
                        .font(.system(size: 14))
                        .foregroundStyle(.secondaryText)
                } else {
                    ForEach(Array(lows.enumerated()), id: \.offset) { index, entry in
                        historicalPriceRow(
                            label: entry.0,
                            price: entry.1,
                            accent: priceAccent(for: index)
                        )
                    }
                }

                currentOffers(prices)

            case .unavailable:
                UnavailablePromptView(message: "No historical price data", retry: retry)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 当前可购买的 Steam 激活价格 / Current prices for Steam-activating offers
    @ViewBuilder
    private func currentOffers(_ prices: ITADPriceGame) -> some View {
        let offers = (offerFilter == .steam ? prices.steamDeals : prices.deals)
            .sorted { $0.price.amountInt < $1.price.amountInt }

        if !offers.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text("CURRENT OFFERS")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(.brandGreen)

                }
                .padding(.top, 14)

                Picker("Offer filter", selection: $offerFilter) {
                    ForEach(OfferFilter.allCases, id: \.self) { filter in
                        let count = filter == .steam ? prices.steamDeals.count : prices.deals.count
                        Text("\(filter.rawValue) (\(count))")
                            .tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                .tint(.brandBlue)

                ForEach(Array(offers.enumerated()), id: \.offset) { index, offer in
                    currentOfferRow(
                        offer,
                        isBest: index == 0
                    )
                }
            }
        } else if offerFilter == .all {
            Text("No current offers")
                .font(.system(size: 14))
                .foregroundStyle(.secondaryText)
        }
    }

    /// 当前报价行，最低价用品牌色标记 / Current offer row; the cheapest offer gets the brand accent
    private func currentOfferRow(
        _ offer: ITADPriceDeal,
        isBest: Bool
    ) -> some View {
        let drmNames = offer.drm.map(\.name).joined(separator: ", ")

        return Button {
            guard let url = URL(string: offer.url) else { return }
            openURL(url)
        } label: {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 3, style: .continuous)
                    .fill(isBest ? Color.brandGreen : Color.secondaryText.opacity(0.5))
                    .frame(width: 5, height: 42)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 8) {
                        Text(offer.shop.name)
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                            .foregroundStyle(.primaryText)

                        if isBest {
                            Text("BEST")
                                .font(.system(size: 10, weight: .bold, design: .rounded))
                                .foregroundStyle(.appBackground)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(Color.brandGreen, in: Capsule())
                        }
                    }

                    Text(offer.cut > 0 ? "Save \(offer.cut)%" : "Current price")
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundStyle(offer.cut > 0 ? .brandBlue : .secondaryText)

                    Text(drmNames.isEmpty ? "DRM unavailable" : drmNames)
                        .font(.system(size: 11, weight: .semibold, design: .rounded))
                        .foregroundStyle(.secondaryText)
                        .lineLimit(1)
                }

                Spacer(minLength: 8)

                VStack(alignment: .trailing, spacing: 3) {
                    Text(offer.price.amount.formatted(.currency(code: offer.price.currency)))
                        .font(.system(size: 19, weight: .bold, design: .monospaced))
                        .foregroundStyle(isBest ? .brandGreen : .primaryText)

                    if offer.cut > 0 {
                        Text(
                            offer.regular.amount.formatted(.currency(code: offer.regular.currency))
                        )
                        .font(.system(size: 12, weight: .medium, design: .monospaced))
                        .foregroundStyle(.secondaryText)
                        .strikethrough()
                    }
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.vertical, 8)
        .overlay(alignment: .bottom) {
            Divider()
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "\(offer.shop.name), \(drmNames.isEmpty ? "DRM unavailable" : drmNames), "
                + "\(offer.price.amount.formatted(.currency(code: offer.price.currency)))"
        )
    }

    /// 一条历史价格 / One historical price row
    private func historicalPriceRow(label: String, price: ITADPrice, accent: Color) -> some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(accent)
                .frame(width: 5, height: 42)

            Text(label)
                .font(.system(size: 16, weight: .semibold, design: .rounded))
                .foregroundStyle(.primaryText)

            Spacer(minLength: 12)

            Text(price.amount.formatted(.currency(code: price.currency)))
                .font(.system(size: 20, weight: .bold, design: .monospaced))
                .foregroundStyle(accent)
        }
        .padding(.vertical, 6)
        .overlay(alignment: .bottom) {
            Divider()
        }
    }

    private func priceAccent(for index: Int) -> Color {
        switch index {
        case 0:
            return .brandGreen
        case 1:
            return .brandBlue
        default:
            return .secondaryText
        }
    }
}

/// 报价筛选：只看 Steam 激活，或全部商店 / The offer filter: Steam activation only, or every shop
enum OfferFilter: String, CaseIterable {
    case steam = "Steam"
    case all = "All"
}

#Preview("加载完 / Loaded") {
    @Previewable @State var offerFilter: OfferFilter = .steam

    ScrollView {
        PriceSectionView(
            state: .loaded(.previewSample),
            offerFilter: $offerFilter,
            retry: {}
        )
        .padding(20)
    }
    .background(Color.appBackground)
}

#Preview("其他状态 / Other states") {
    ScrollView {
        VStack(alignment: .leading, spacing: 24) {
            PriceSectionView(state: .loading, offerFilter: .constant(.steam), retry: {})
            PriceSectionView(state: .free, offerFilter: .constant(.steam), retry: {})
            PriceSectionView(state: .demo, offerFilter: .constant(.steam), retry: {})
            PriceSectionView(state: .comingSoon, offerFilter: .constant(.steam), retry: {})
            PriceSectionView(state: .unavailable, offerFilter: .constant(.steam), retry: {})
        }
        .padding(20)
    }
    .background(Color.appBackground)
}
