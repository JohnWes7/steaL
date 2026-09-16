//
//  SignOutSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/17.
//

import SwiftUI

/// Profile 底部的登出卡片；点击尚未接入 / The sign-out card at the bottom of Profile; the tap is not wired yet
struct SignOutSectionView: View {

    var body: some View {
        HStack(spacing: 12) {
            IconTileView(icon: "rectangle.portrait.and.arrow.right", tint: .red.opacity(0.75))

            Text("Sign Out")
                .font(.system(size: 16, weight: .semibold, design: .rounded))
                .foregroundStyle(.red)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Sign Out")
    }
}

#Preview {
    SignOutSectionView()
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.appBackground)
}
