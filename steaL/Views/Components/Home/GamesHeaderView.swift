//
//  GamesHeaderView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 主页顶部栏内容：品牌标题 + 通知入口；内边距自带，页面只管放进 `safeAreaBar` /
/// Top bar content: brand title and notification entry; owns its padding, pages only place it in `safeAreaBar`
struct GamesHeaderView: View {

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            header
        }
        .padding(.horizontal, 14)
        .padding(.top, 2)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 品牌标题区：左侧标语与 steaL 字样，右侧通知入口 / Brand header: tagline and steaL wordmark on the left, notification entry on the right
    @ViewBuilder
    private var header: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 0) {
                Text("STEAM PRICE TRACKER")
                    .font(.system(size: 13, weight: .bold, design: .default))
                    .foregroundStyle(.brandGreen)

                HStack(spacing: 0) {
                    Text("stea")
                        .foregroundStyle(.primaryText)

                    Text("L")
                        .foregroundStyle(.brandBlue)
                }
                .font(.system(size: 44, weight: .bold, design: .default))
            }

            Spacer()

            notificationButton
        }
    }

    /// 右上角通知按钮，附带未读小红点 / Notification button in the top-right, with an unread badge
    @ViewBuilder
    private var notificationButton: some View {
        Button {
            // 打开通知页面
        } label: {
            Image(systemName: "bell.fill")
                .font(.system(size: 21, weight: .semibold))
                .foregroundStyle(.primaryText)
                .frame(width: 56, height: 56)
                .background(Color.surfaceElevated)
                .clipShape(Circle())
                .overlay(alignment: .topTrailing) {
                    Circle()
                        .fill(Color.brandBlue)
                        .frame(width: 11, height: 11)
                        .offset(x: -8, y: 8)
                }
        }
        .accessibilityLabel("Notifications")
    }
}

#Preview {
    GamesHeaderView()
        .background(.bar)
}
