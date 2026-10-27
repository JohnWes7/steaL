//
//  OnboardingSheet.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import SwiftUI

/// 第一次打开 App 时的欢迎页：几行功能介绍和一个 Continue 按钮。
/// The welcome sheet shown on first launch: a few feature rows and a Continue button.
///
/// 只读不操作，点 Continue 就关。HIG 要求引导「简短、可跳过、之后能再找到」，所以 sheet 自己不记是否看过，
/// 由弹出它的页面在关闭时记下，Profile 也能再弹同一张。
/// Read-only apart from Continue, which dismisses it. The HIG asks for onboarding that's brief, skippable and easy
/// to find again, so the sheet doesn't record whether it was seen; the presenter records that on dismiss, and
/// Profile can present the same sheet again.
struct OnboardingSheet: View {

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(spacing: 40) {
                header
                features
            }
            .padding(.horizontal, 32)
            .padding(.top, 56)
            .padding(.bottom, 24)
        }
        .scrollBounceBehavior(.basedOnSize)
        // 按钮固定在底部，大字号时内容滚动、按钮不跑。
        // The button stays pinned at the bottom; at large sizes the content scrolls beneath it.
        .safeAreaBar(edge: .bottom) {
            continueButton
        }
        .background(Color.appBackground)
    }

    // MARK: - 头部 / Header

    /// App 图标和欢迎标题。
    /// The app icon and the welcome title.
    private var header: some View {
        VStack(spacing: 16) {
            // App Icon 的静态导出，圆角已在图里。
            // Static export of the app icon, with the corners baked in.
            Image(.appIcon)
                .resizable()
                .frame(width: 80, height: 80)
                .accessibilityHidden(true)

            Text("Welcome to steaL")
                .font(.system(.largeTitle, design: .rounded, weight: .bold))
                .foregroundStyle(.primaryText)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - 功能 / Features

    /// 四行功能介绍，顺序与第一次使用时碰到它们的顺序一致。
    /// The four feature rows, in the order people meet them on first use.
    private var features: some View {
        VStack(alignment: .leading, spacing: 28) {
            OnboardingFeatureRow(
                systemImage: "tag.fill",
                title: "Find Deals",
                description: "Browse today's best Steam deals and free giveaways on the Games tab."
            )

            OnboardingFeatureRow(
                systemImage: "chart.line.downtrend.xyaxis",
                title: "Compare Prices",
                description: "Open any game to see its price in every store and its historical low."
            )

            OnboardingFeatureRow(
                systemImage: "heart.fill",
                title: "Build a Wishlist",
                description: "Tap the heart to save a game and track its price. Sign in to keep your wishlist."
            )

            OnboardingFeatureRow(
                systemImage: "sparkles",
                title: "Ask AI Before You Buy",
                description: "Tap the sparkles on a game page to find out whether to buy now or wait."
            )
        }
    }

    // MARK: - 按钮 / Button

    /// 唯一的操作：关掉欢迎页。
    /// The only action: dismisses the welcome sheet.
    private var continueButton: some View {
        Button {
            dismiss()
        } label: {
            Text("Continue")
                .font(.system(.headline, design: .rounded))
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.extraLarge)
        .tint(.brandGreen)
        .foregroundStyle(.onBrandGreen)
        .padding(.horizontal, 32)
        .padding(.bottom, 16)
    }
}

#Preview {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            OnboardingSheet()
                .interactiveDismissDisabled()
        }
}
