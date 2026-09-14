//
//  UnavailablePromptView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 可选数据区块「问过了但没有内容」时的提示：一行说明加一个蓝色 Retry /
/// The prompt of an optional section that has nothing to show: one line of copy plus a blue Retry
struct UnavailablePromptView: View {

    /// 说明文案 / The explanation
    let message: String

    /// 重试动作，异步工作由调用方包好 / The retry action; the caller wraps the async work
    let retry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(.secondaryText)

            Button("Retry", action: retry)
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(.brandBlue)
                .buttonStyle(.plain)
        }
    }
}

#Preview {
    UnavailablePromptView(message: "No historical price data", retry: {})
        .padding(20)
        .background(Color.appBackground)
}
