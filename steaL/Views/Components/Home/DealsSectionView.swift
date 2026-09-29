//
//  DealsSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 「Hot Deals」区块内容：按自己的状态机渲染加载 / 失败 / 空 / 列表，标题与跳转留在页面 /
/// Hot Deals section content; renders loading, failure, empty or the list from its own state
struct DealsSectionView: View {

    /// 区块状态 / The state of the section
    let state: GamesViewModel.DealLoadState

    /// 收藏判定，收藏状态由页面持有 / Favourite lookup; the page owns that state
    let isFavorite: (HomeDealItem) -> Bool

    /// 切换收藏 / Toggles the favourite
    let onToggleFavorite: (HomeDealItem) -> Void

    /// 点击某一条，去哪里由页面决定 / Reports a tap; the page decides where it goes
    let onSelect: (HomeDealItem) -> Void

    /// 重试动作，异步工作由页面包好 / The retry action; the page wraps the async work
    let retry: () -> Void

    /// 自适应网格列：竖屏两列，横屏按宽度放更多列，卡片大小不变。
    /// Adaptive grid columns: two in portrait, more in landscape, with cards keeping their size.
    ///
    /// 最小 160 让所有 iPhone 竖屏都正好两列（SE 放得下两列，Pro Max 放不下三列），列宽与原来的两列平分一致；
    /// 最大 200 是 Pro Max 竖屏的列宽，更宽的屏幕上卡片不再被拉大。
    /// A 160 minimum keeps every iPhone at exactly two columns in portrait (two fit on SE, three don't
    /// on Pro Max), matching the old equal split; the 200 maximum is the Pro Max portrait width, so
    /// wider screens don't stretch the cards.
    private let gridColumns = [
        GridItem(.adaptive(minimum: 160, maximum: 200), spacing: 12),
    ]

    var body: some View {
        switch state {
        case .loading:
            SectionLoadingView()

        case .failed(let error):
            LoadErrorView(error: error, retry: retry)

        case .loaded(let items):
            if items.isEmpty {
                emptyState
            } else {
                content(items)
            }
        }
    }

    /// 请求成功但列表为空时的提示，附刷新入口 /
    /// Note for a successful but empty list, with a refresh action
    private var emptyState: some View {
        // 样式同 `LoadErrorView`：默认颜色，系统文字按钮 / Styled like `LoadErrorView`: default colours, a plain text button
        ContentUnavailableView {
            Label("No Deals Found", systemImage: "tag.slash")
        } description: {
            Text("There are no discounted games to show right now. Try again later.")
        } actions: {
            Button("Refresh", action: retry)
                .tint(.brandBlue)
        }
        .frame(maxWidth: .infinity)
    }

    /// 首条 hero 卡 + 其余自适应网格；收 `HomeDealItem` 是因为每行还要带 Steam appID 的解析结果 /
    /// Hero card first, then an adaptive grid; each row also carries its Steam appID resolution
    @ViewBuilder
    private func content(_ items: [HomeDealItem]) -> some View {

        // 首条 hero 卡 / Hero card for the first item
        if let firstItem = items.first {
            // 卡片包成 `Button`，`.plain` 让卡内收藏按钮的点击不被外层吞掉 / `.plain` keeps the card's own favourite button clickable
            Button {
                onSelect(firstItem)
            } label: {
                DealCardView(
                    deal: firstItem.deal,
                    isFavorite: isFavorite(firstItem),
                    isFavoriteEnabled: firstItem.appID != nil,
                    onToggleFavorite: {
                        onToggleFavorite(firstItem)
                    }
                )
            }
            .buttonStyle(.plain)
        }

        // 其余条目排成自适应网格（竖屏两列） / Rest of the items in an adaptive grid (two columns in portrait)
        LazyVGrid(columns: gridColumns, spacing: 12) {
            ForEach(items.dropFirst()) { item in
                Button {
                    onSelect(item)
                } label: {
                    DealGridCardView(
                        deal: item.deal,
                        isFavorite: isFavorite(item),
                        isFavoriteEnabled: item.appID != nil,
                        onToggleFavorite: {
                            onToggleFavorite(item)
                        }
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }
}

#Preview("有内容 / Loaded") {
    ScrollView {
        DealsSectionView(
            state: .loaded([HomeDealItem(deal: .previewSample)]),
            isFavorite: { _ in false },
            onToggleFavorite: { _ in },
            onSelect: { _ in },
            retry: {}
        )
        .padding(14)
    }
    .background(Color.appBackground)
}

#Preview("其他状态 / Other states") {
    ScrollView {
        VStack(spacing: 24) {
            DealsSectionView(state: .loading, isFavorite: { _ in false }, onToggleFavorite: { _ in }, onSelect: { _ in }, retry: {})
            DealsSectionView(state: .failed(.offline), isFavorite: { _ in false }, onToggleFavorite: { _ in }, onSelect: { _ in }, retry: {})
            DealsSectionView(state: .loaded([]), isFavorite: { _ in false }, onToggleFavorite: { _ in }, onSelect: { _ in }, retry: {})
        }
        .padding(14)
    }
    .background(Color.appBackground)
}
