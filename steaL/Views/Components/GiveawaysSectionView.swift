//
//  GiveawaysSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 「Giveaways」区块内容：跟随自己的状态机渲染加载 / 失败 / 空 / 行列表 /
/// Giveaways section content; renders loading, failure, empty or the rows from its own state
/// machine
///
/// 标题仍由 `GamesView` 的 `Section { } header: { }` 提供，所以这里只有内容 ——
/// 区块间距由 `LazyVStack` 与 header 的 padding 决定，搬进组件反而要重新对一遍 /
/// The title stays with `GamesView`'s `Section { } header: { }`, so this view is content only:
/// the section rhythm comes from the `LazyVStack` and the header's padding
struct GiveawaysSectionView: View {

    /// 区块状态 / The state of the section
    let state: GamesViewModel.GiveawaysLoadState

    /// 重试动作，异步工作由页面包好 / The retry action; the page wraps the async work
    let retry: () -> Void

    var body: some View {
        switch state {
        case .loading:
            SectionLoadingView()

        case .failed(let error):
            SectionErrorView(error: error, retry: retry)

        case .loaded(let giveaways):
            if giveaways.isEmpty {
                emptyNote
            } else {
                VStack(spacing: 10) {
                    ForEach(giveaways) { giveaway in
                        GiveawayRowView(giveaway: giveaway)
                    }
                }
            }
        }
    }

    /// 没有 giveaway 在送时的轻量提示 / Lightweight note when no giveaways are active
    private var emptyNote: some View {
        Text("No giveaways right now. Check back later.")
            .font(.system(size: 14))
            .foregroundStyle(.secondaryText)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview("有内容 / Loaded") {
    GiveawaysSectionView(state: .loaded([.previewSample, .previewSampleFallback]), retry: {})
        .padding(14)
        .background(Color.appBackground)
}

#Preview("加载中 / Loading") {
    GiveawaysSectionView(state: .loading, retry: {})
        .padding(14)
        .background(Color.appBackground)
}

#Preview("失败 / Failure") {
    GiveawaysSectionView(state: .failed(.offline), retry: {})
        .padding(14)
        .background(Color.appBackground)
}
