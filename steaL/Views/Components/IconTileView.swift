//
//  IconTileView.swift
//  steaL
//
//  Created by yijiang on 2026/9/17.
//

import SwiftUI

/// 设置行左侧的圆角图标方块，Profile 的偏好与通知分组共用 /
/// The rounded icon tile at the leading edge of a settings row, shared by both profile groups
struct IconTileView: View {

    /// SF Symbols 名称 / The SF Symbol name
    let icon: String

    /// 方块底色 / The tile's fill color
    let tint: Color

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: 15, weight: .bold))
            .foregroundStyle(.white)
            .frame(width: 32, height: 32)
            .background(tint, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
    }
}

#Preview {
    HStack(spacing: 12) {
        IconTileView(icon: "globe.asia.australia.fill", tint: .brandGreen)
        IconTileView(icon: "moon.fill", tint: .secondaryText)
        IconTileView(icon: "bell.fill", tint: .brandBlue)
    }
    .padding(14)
    .background(Color.surfaceElevated)
}
