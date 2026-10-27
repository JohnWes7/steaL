//
//  RetryingAsyncImage.swift
//  steaL
//
//  Created by yijiang on 2026/9/18.
//

import SwiftUI

/// 会重试的 `AsyncImage`：原生的在视图消失时取消下载、重新出现时却不重发，phase 会永久停在占位态 /
/// An `AsyncImage` that retries: the stock one cancels on disappear and never re-requests, leaving
/// the phase stuck on a placeholder
struct RetryingAsyncImage<Content: View>: View {

    /// 图片地址 / Image URL
    let url: URL?

    /// 按 phase 渲染的内容，签名与 `AsyncImage` 一致 / Phase-driven content, matching `AsyncImage`'s signature
    @ViewBuilder let content: (AsyncImagePhase) -> Content

    /// 重试计数，同时是 `AsyncImage` 的 identity：一变就重建、重新发请求 /
    /// Retry counter, doubling as the `AsyncImage` identity: bumping it rebuilds and refetches
    @State private var attempt = 0

    /// 是否成功加载过，成功过就不再重试 / Whether the image ever loaded; once it has, no retry fires
    @State private var hasLoaded = false

    /// 是否已经出现过一次 / Whether the view has appeared before
    @State private var hasAppeared = false

    /// 切后台同样会取消请求，症状与切 Tab 一致 / Backgrounding cancels the request too, same symptom
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        AsyncImage(url: url) { phase in
            // `AsyncImagePhase` 不是 `Equatable`，转成 `Bool` 才能交给 `onChange` /
            // `AsyncImagePhase` is not `Equatable`; a `Bool` is what `onChange` can compare
            let didSucceed = phase.image != nil

            content(phase)
                .onChange(of: didSucceed, initial: true) { _, succeeded in
                    if succeeded { hasLoaded = true }
                }
        }
        .id(attempt)
        .onAppear {
            // 首次出现让 `AsyncImage` 自己跑，立刻换 id 会白发一次请求 /
            // The first appearance is left alone; bumping the id at once would waste a request
            guard hasAppeared else {
                hasAppeared = true
                return
            }
            retryIfNeeded()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { retryIfNeeded() }
        }
    }

    // MARK: - 重试 / Retry

    /// 只有没成功过才重建 / Rebuilds only when the image never loaded
    private func retryIfNeeded() {
        guard !hasLoaded else { return }
        attempt += 1
    }
}

#Preview {
    RetryingAsyncImage(url: URL(string: "https://example.com/missing.jpg")) { phase in
        switch phase {
        case .success(let image):
            image
                .resizable()
                .scaledToFill()
        default:
            Color.imagePlaceholder
        }
    }
    .frame(width: 320, height: 160)
}
