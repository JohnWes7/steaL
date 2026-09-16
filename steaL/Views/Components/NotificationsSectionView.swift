//
//  NotificationsSectionView.swift
//  steaL
//
//  Created by yijiang on 2026/9/17.
//

import SwiftUI

/// Profile 通知分组：降价与发售两个开关；写入设置存储后再接交互 /
/// The notifications group: the price-drop and release switches, read-only until the writes are wired
struct NotificationsSectionView: View {

    /// 是否开启降价提醒 / Whether price-drop alerts are on
    let isPriceDropAlertsEnabled: Bool

    /// 是否开启发售提醒 / Whether release alerts are on
    let isReleaseAlertsEnabled: Bool

    var body: some View {
        VStack(spacing: 0) {
            switchRow(
                icon: "bell.fill",
                label: "Price Drop Alerts",
                tint: .brandBlue,
                isOn: isPriceDropAlertsEnabled
            )

            Divider()

            switchRow(
                icon: "calendar.badge.clock",
                label: "Game Release Alerts",
                tint: .secondaryText,
                isOn: isReleaseAlertsEnabled
            )
        }
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    /// 一行开关；点击尚未接入，所以绑定是常量，开关只反映存储里的值 /
    /// One switch row; the binding is constant because tapping is not wired yet, so the switch only mirrors what is stored
    private func switchRow(icon: String, label: String, tint: Color, isOn: Bool) -> some View {
        Toggle(isOn: .constant(isOn)) {
            HStack(spacing: 12) {
                IconTileView(icon: icon, tint: tint)

                Text(label)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundStyle(.primaryText)

                Spacer(minLength: 12)
            }
        }
        .tint(.brandGreen)
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
    }
}

#Preview {
    NotificationsSectionView(isPriceDropAlertsEnabled: true, isReleaseAlertsEnabled: false)
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.appBackground)
}
