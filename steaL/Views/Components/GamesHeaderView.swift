//
//  GamesHeaderView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 主页顶部栏内容：品牌标题 + 通知入口 + 搜索框；背景与内边距写在调用点 /
/// Top bar content: brand title, notification entry and search field; padding lives at the call site
struct GamesHeaderView: View {

    /// 点击搜索入口时交给调用方处理 / Handed to the caller when the search affordance is tapped
    // let onSearchTapped: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            header
            // searchField
        }
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

    /// 搜索入口：与 `SearchView` 的输入框共用 `SearchBarShell`，点击只发出切 tab 的信号 /
    /// Search entry point, sharing `SearchBarShell`; a tap only signals "switch to search"
    // @ViewBuilder
    // private var searchField: some View {
    //     SearchBarButton(placeholder: "Search games, publishers...", action: onSearchTapped)
    // }
}

#Preview {
    GamesHeaderView()
        .padding(.horizontal, 14)
        .padding(.top, 2)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.bar)
}
