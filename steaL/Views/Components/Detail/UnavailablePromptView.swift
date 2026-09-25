//
//  UnavailablePromptView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 可选数据区块没有内容时的提示：一行说明；请求失败时再加一个蓝色 Retry，查了没有就不给（重试也不会变）/
/// The prompt of an optional section with nothing to show: one line of copy, plus a blue Retry only
/// when the request failed (a plain miss gets none, since retrying would not change it)
struct UnavailablePromptView: View {

    /// 说明文案 / The explanation
    let message: String

    /// 重试动作，异步工作由调用方包好；`nil` 不显示按钮 /
    /// The retry action, the caller wrapping the async work; `nil` hides the button
    var retry: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondaryText)

            if let retry {
                Button("Retry", action: retry)
                    .font(.system(.subheadline, design: .rounded, weight: .semibold))
                    .foregroundStyle(.brandBlue)
                    .buttonStyle(.plain)
            }
        }
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 24) {
        UnavailablePromptView(message: "Couldn't load prices.", retry: {})
        UnavailablePromptView(message: "No prices found for this game.")
    }
    .padding(20)
    .background(Color.appBackground)
}
