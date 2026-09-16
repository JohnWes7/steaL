//
//  ProfileIdentityCardView.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import SwiftUI

/// Profile 身份卡：品牌渐变发丝线、头像、姓名邮箱与两项追踪统计 /
/// Profile identity card: brand gradient hairline, avatar, name and email, and two tracking stats
struct ProfileIdentityCardView: View {

    /// 要展示的用户 / The user shown
    let user: ProfileUser

    /// 发丝线与头像共用的品牌渐变，全页唯一的装饰元素 /
    /// The brand gradient shared by the hairline and the avatar; the page's single decorative gesture
    private var brandGradient: LinearGradient {
        LinearGradient(colors: [.brandGreen, .brandBlue], startPoint: .leading, endPoint: .trailing)
    }

    var body: some View {
        VStack(spacing: 0) {
            brandGradient
                .frame(height: 2)

            identityRow

            Divider()

            statsRow
        }
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    // MARK: - 身份行 / Identity

    /// 头像、姓名与邮箱 / Avatar, name and email
    private var identityRow: some View {
        HStack(spacing: 14) {
            avatar

            VStack(alignment: .leading, spacing: 3) {
                Text(user.displayName)
                    .font(.system(size: 20, weight: .bold, design: .default))
                    .foregroundStyle(.primaryText)
                    .lineLimit(1)

                Text(user.email)
                    .font(.system(size: 14, weight: .regular, design: .default))
                    .foregroundStyle(.secondaryText)
                    .lineLimit(1)
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(user.displayName), \(user.email)")
    }

    /// 首字母头像，填充与发丝线同一道渐变 / Monogram avatar filled with the same gradient as the hairline
    private var avatar: some View {
        Text(user.initial)
            .font(.system(size: 26, weight: .bold, design: .default))
            .foregroundStyle(.white)
            .frame(width: 64, height: 64)
            .background(brandGradient, in: Circle())
    }

    // MARK: - 统计 / Stats

    /// 两项统计并排，中间一条竖线分隔 / The two stats side by side, split by a vertical rule
    private var statsRow: some View {
        HStack(spacing: 0) {
            stat(value: user.trackingCount, label: "Tracking", tint: .brandGreen)

            Divider()
                .frame(height: 34)
                .padding(.horizontal, 16)

            stat(value: user.alertsSentCount, label: "Alerts sent", tint: .brandBlue)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
    }

    /// 一项统计：数值在上、标签在下，与详情页的 ITAD 指标同一套排版 /
    /// One stat, figure above its label, matching the ITAD metrics on the detail page
    private func stat(value: Int, label: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value.formatted())
                .font(.system(size: 20, weight: .heavy, design: .rounded))
                .foregroundStyle(tint)

            Text(label)
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundStyle(.secondaryText)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label), \(value)")
    }
}

#Preview {
    ProfileIdentityCardView(user: .mock)
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.appBackground)
}
