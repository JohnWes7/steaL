//
//  SectionStatusViews.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 区块内加载中：一块局部转圈，不把同页其它区块也打回加载态 /
/// In-section loading: a local spinner that leaves the other sections on the page alone
struct SectionLoadingView: View {

    var body: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 40)
    }
}

/// 区块内加载失败，附重试入口 / In-section failure with a retry action
struct SectionErrorView: View {

    /// 归一化后的错误，标题、说明与图标都取自它 /
    /// The normalised error; its title, message and symbol are used as-is
    let error: LoadError

    /// 重试动作，异步工作由调用方包好 / The retry action; the caller wraps the async work
    let retry: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label(error.title, systemImage: error.symbolName)
                .foregroundStyle(.secondaryText)
        } description: {
            Text(error.message)
                .foregroundStyle(.secondaryText)
        } actions: {
            SectionRetryButton(title: "Try Again", action: retry)
        }
        .frame(maxWidth: .infinity)
    }
}

/// 失败态与空态共用的重试按钮 / Retry button shared by the failure and the empty states
struct SectionRetryButton: View {

    /// 按钮文案：失败态用 `Try Again`，空列表用 `Refresh` /
    /// Button copy: `Try Again` for failures, `Refresh` for empty lists
    let title: String

    /// 点击动作 / The tap action
    let action: () -> Void

    var body: some View {
        Button(title, action: action)
            .buttonStyle(.borderedProminent)
            .tint(.brandBlue)
    }
}

#Preview("加载中 / Loading") {
    SectionLoadingView()
        .background(Color.appBackground)
}

#Preview("失败 / Failure") {
    SectionErrorView(error: .offline, retry: {})
        .background(Color.appBackground)
}

#Preview("空列表按钮 / Empty-list button") {
    SectionRetryButton(title: "Refresh", action: {})
        .padding(20)
        .background(Color.appBackground)
}
