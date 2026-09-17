//
//  PriceSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 报价筛选：只看 Steam 激活，或全部商店 / The offer filter: Steam activation only, or every shop
enum OfferFilter: String, CaseIterable {
    case steam = "Steam"
    case all = "All"
}

/// 历史价格窗口与当前报价；比价晚于详情到达，所以自带小加载态、不拖整页 /
/// The historical price windows and current offers; it carries its own little loading state
struct PriceSectionView: View {

    /// 比价区块的状态 / The state of the price section
    let state: GameDetailViewModel.PricesState

    /// 用户选的报价筛选；状态由详情页持有，重试后不会丢 /
    /// The offer filter chosen by the user; the page owns the state so a retry keeps it
    @Binding var offerFilter: OfferFilter

    /// 是否展开全部报价，收起时只留最便宜的一条；状态由详情页持有 /
    /// Whether every offer is shown, collapsed keeps only the cheapest; the page owns the state
    @Binding var isShowingAllOffers: Bool

    /// 重试动作 / The retry action
    let retry: () -> Void

    /// 用来打开外部商店页 / Used to open the external store page
    @Environment(\.openURL) private var openURL

    /// 用户开了「减弱动态效果」时跳过展开动画 /
    /// Skips the expand animation when Reduce Motion is turned on
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // `.hidden` 连标题一起不渲染 / `.hidden` takes the title down with it
        if state != .hidden {
            section
        }
    }

    /// 区块本体 / The section itself
    private var section: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("PRICES")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.brandGreen)

            switch state {
            case .loading:
                ProgressView()
                    .controlSize(.small)

            case .loaded(let prices):
                priceHistory(prices)
                currentOffers(prices)

            case .loadedWithoutHistory(let prices):
                currentOffers(prices)

            case .unavailable:
                UnavailablePromptView(message: "No historical price data", retry: retry)

            // `body` 已经挡掉 `.hidden` / `body` filters `.hidden` out
            case .hidden:
                EmptyView()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 三档历史低价；都没有就退成一行说明 /
    /// The historical lows of the three windows, collapsing into one line when there are none
    @ViewBuilder
    private func priceHistory(_ prices: ITADPriceGame) -> some View {
        let lows: [(String, ITADPrice)] = [
            ("All-time low", prices.historyLow.all),
            ("1 year low", prices.historyLow.y1),
            ("3 months low", prices.historyLow.m3),
        ].compactMap { label, price in
            guard let price else { return nil }
            return (label, price)
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
    }

    /// 当前可购买的 Steam 激活价格；默认只露最便宜的一条 /
    /// Current prices for Steam-activating offers; only the cheapest one shows by default
    @ViewBuilder
    private func currentOffers(_ prices: ITADPriceGame) -> some View {
        let (selected, best) = switch offerFilter {
        case .steam: (prices.steamDeals, prices.bestSteamDeal)
        case .all: (prices.deals, prices.bestDeal)
        }
        let offers = selected.sorted { $0.price.amountInt < $1.price.amountInt }
        let visibleOffers = isShowingAllOffers ? offers : Array(offers.prefix(1))

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

                ForEach(visibleOffers, id: \.self) { offer in
                    currentOfferRow(offer, isBest: offer == best)
                }

                if offers.count > 1 {
                    HStack(spacing: 0) {
                        Spacer(minLength: 0)

                        Button {
                            toggleOffers()
                        } label: {
                            MoreToggleLabel(isExpanded: isShowingAllOffers)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(isShowingAllOffers ? "Show less" : "Show more")
                    }
                }
            }
        } else if offerFilter == .all {
            Text("No current offers")
                .font(.system(size: 14))
                .foregroundStyle(.secondaryText)
        }
    }

    /// 当前报价行，最低价整行换成实心绿块 / Current offer row; the cheapest one turns into a solid green slab
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
                if !isBest {
                    RoundedRectangle(cornerRadius: 3, style: .continuous)
                        .fill(Color.secondaryText.opacity(0.5))
                        .frame(width: 5, height: 42)
                }

                offerInfo(offer, isBest: isBest, drmNames: drmNames)

                Spacer(minLength: 8)

                offerPrice(offer, isBest: isBest)

                // 出 App 的符号每行都挂；绿块上是深色，普通行用次要灰，12 个图标才不至于糊成一片 /
                // Every row carries the "leaves the app" mark: dark on the slab, muted grey on the plain
                // rows, so twelve of them stay quiet
                Image(systemName: "arrow.up.forward.square")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(isBest ? Color.appBackground : Color.secondaryText)
            }
            // 图标槽由符号自己占出来（12 间距 + 20 图标），两侧留白每行一致，价格才对成一列 /
            // The mark itself reserves the slot (12 spacing + 20 icon); matching trailing padding on every
            // row is what keeps the prices on one column
            .padding(.leading, isBest ? 16 : 0)
            .padding(.trailing, 16)
            .padding(.vertical, isBest ? 14 : 0)
            // 最低价整块换成实心绿，配方照搬试玩版入口，绿底 = 点下去会离开 App 去商店 /
            // The cheapest offer becomes a solid green slab; green means the tap leaves the app
            .background(
                isBest ? Color.brandGreen : Color.clear,
                in: RoundedRectangle(cornerRadius: 16, style: .continuous)
            )
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
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

    /// 报价左栏：商店、BEST 徽标、折扣与 DRM / The left column: shop, BEST badge, discount and DRM
    private func offerInfo(
        _ offer: ITADPriceDeal,
        isBest: Bool,
        drmNames: String
    ) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 8) {
                Text(offer.shop.name)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundStyle(isBest ? Color.appBackground : Color.primaryText)

                if isBest {
                    Text("BEST")
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .foregroundStyle(.brandGreen)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Color.appBackground, in: Capsule())
                }
            }

            Text(offer.cut > 0 ? "Save \(offer.cut)%" : "Current price")
                .font(.system(size: 13, weight: .medium, design: .rounded))
                .foregroundStyle(
                    isBest
                        ? Color.appBackground.opacity(0.72)
                        : (offer.cut > 0 ? Color.brandBlue : Color.secondaryText)
                )

            Text(drmNames.isEmpty ? "DRM unavailable" : drmNames)
                .font(.system(size: 11, weight: .semibold, design: .rounded))
                .foregroundStyle(isBest ? Color.appBackground.opacity(0.72) : Color.secondaryText)
                .lineLimit(1)
        }
    }

    /// 报价右栏：现价与划线原价 / The right column: the current price and the struck-through regular price
    private func offerPrice(_ offer: ITADPriceDeal, isBest: Bool) -> some View {
        VStack(alignment: .trailing, spacing: 3) {
            Text(priceText(offer.price))
                .font(.system(size: 19, weight: .bold, design: .monospaced))
                .foregroundStyle(isBest ? Color.appBackground : Color.primaryText)

            if offer.cut > 0 {
                Text(
                    offer.regular.amount.formatted(.currency(code: offer.regular.currency))
                )
                .font(.system(size: 12, weight: .medium, design: .monospaced))
                .foregroundStyle(isBest ? Color.appBackground.opacity(0.72) : Color.secondaryText)
                .strikethrough()
            }
        }
    }

    /// 展开与收起全部报价；开了减弱动态效果就直接切换，不带动画 /
    /// Toggles the full offer list; with Reduce Motion on it switches without the transition
    private func toggleOffers() {
        if reduceMotion {
            isShowingAllOffers.toggle()
        } else {
            withAnimation(.snappy(duration: 0.3)) {
                isShowingAllOffers.toggle()
            }
        }
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

            Text(priceText(price))
                .font(.system(size: 20, weight: .bold, design: .monospaced))
                .foregroundStyle(accent)
        }
        .padding(.vertical, 6)
        .overlay(alignment: .bottom) {
            Divider()
        }
    }

    /// 价格文本：0 元一律显示 `Free`，其余按数据自带的货币格式化 /
    /// The price text: a zero amount always reads `Free`, everything else is formatted in the
    /// payload's own currency
    private func priceText(_ price: ITADPrice) -> String {
        price.amountInt == 0 ? "Free" : price.amount.formatted(.currency(code: price.currency))
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

#Preview("加载完 / Loaded") {
    @Previewable @State var offerFilter: OfferFilter = .steam
    @Previewable @State var isShowingAllOffers = false

    ScrollView {
        PriceSectionView(
            state: .loaded(.previewSample),
            offerFilter: $offerFilter,
            isShowingAllOffers: $isShowingAllOffers,
            retry: {}
        )
        .padding(20)
    }
    .background(Color.appBackground)
}

#Preview("其他状态 / Other states") {
    ScrollView {
        VStack(alignment: .leading, spacing: 24) {
            PriceSectionView(
                state: .loading,
                offerFilter: .constant(.steam),
                isShowingAllOffers: .constant(false),
                retry: {}
            )
            PriceSectionView(
                state: .loadedWithoutHistory(.previewSample),
                offerFilter: .constant(.steam),
                isShowingAllOffers: .constant(false),
                retry: {}
            )
            PriceSectionView(
                state: .unavailable,
                offerFilter: .constant(.steam),
                isShowingAllOffers: .constant(false),
                retry: {}
            )
        }
        .padding(20)
    }
    .background(Color.appBackground)
}
