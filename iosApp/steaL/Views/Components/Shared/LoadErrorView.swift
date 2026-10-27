//
//  LoadErrorView.swift
//  steaL
//
//  Created by yijiang on 2026/9/26.
//

import SwiftUI

/// `LoadError` 唯一的显示方式：标题、说明与图标都取自它，全 App 看起来一样 /
/// The one way a `LoadError` is shown: title, message and symbol come from it, so every page looks the same
struct LoadErrorView: View {

    /// 归一化后的错误 / The normalised error
    let error: LoadError

    /// 重试动作，异步工作由调用方包好；`nil` 不显示按钮（如搜索：改搜索词就会重查）/
    /// The retry action, the caller wrapping the async work; `nil` hides the button (search re-runs on a new query)
    var retry: (() -> Void)?

    var body: some View {
        ContentUnavailableView {
            Label(error.title, systemImage: error.symbolName)
        } description: {
            Text(error.message)
        } actions: {
            // 系统默认的文字按钮（同 Apple 的 `ContentUnavailableView` 示例），只换成品牌色 /
            // The system's plain text button, as in Apple's `ContentUnavailableView` samples, in the brand colour
            if let retry {
                Button("Try Again", action: retry)
                    .tint(.brandBlue)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

#Preview("可重试 / With retry") {
    LoadErrorView(error: .offline, retry: {})
        .background(Color.appBackground)
}

#Preview("不可重试 / Without retry") {
    LoadErrorView(error: .server(statusCode: 500))
        .background(Color.appBackground)
}
