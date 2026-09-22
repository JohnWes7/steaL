//
//  ProfileIdentityCardView.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import SwiftUI

/// Profile 身份卡：品牌渐变发丝线、头像、姓名邮箱与底部区域；登录前后是同一张卡，只换头像、文字与底部 /
/// Profile identity card: brand gradient hairline, avatar, name and email, and a bottom area;
/// one card before and after sign-in, swapping only the avatar, text and bottom area
struct ProfileIdentityCardView: View {

    /// 要展示的用户，`nil` 表示未登录 / The user shown; `nil` means signed out
    let user: ProfileUser?

    /// 点 Sign In，由页面弹出登录 sheet；只在未登录时用到 / Tapping Sign In; the page presents the sign-in sheet. Only used when signed out
    let signIn: () -> Void

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

            if let user {
                statsRow(for: user)
            } else {
                signInButton
            }
        }
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    // MARK: - 身份行 / Identity

    /// 头像、姓名与邮箱；未登录时是 Guest 与一句登录的好处（HIG：先说明登录能换来什么）/
    /// Avatar, name and email; signed out, Guest and one line on what signing in is for (HIG: explain the benefit first)
    private var identityRow: some View {
        HStack(spacing: 14) {
            avatar

            VStack(alignment: .leading, spacing: 3) {
                Text(user?.displayName ?? "Guest")
                    .font(.system(size: 20, weight: .bold, design: .default))
                    .foregroundStyle(.primaryText)
                    .lineLimit(1)

                if let user {
                    Text(user.email)
                        .font(.system(size: 14, weight: .regular, design: .default))
                        .foregroundStyle(.secondaryText)
                        .lineLimit(1)
                } else {
                    Text("Sign in to keep a wishlist on all your devices.")
                        .font(.system(size: 14, weight: .regular, design: .default))
                        .foregroundStyle(.secondaryText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .accessibilityElement(children: .combine)
    }

    /// 登录后是渐变填充的首字母；未登录是同一道渐变只描边的空位 /
    /// Signed in, a monogram filled with the gradient; signed out, an empty slot stroked with the same gradient
    @ViewBuilder
    private var avatar: some View {
        if let user {
            Text(user.initial)
                .font(.system(size: 26, weight: .bold, design: .default))
                .foregroundStyle(.white)
                .frame(width: 64, height: 64)
                .background(brandGradient, in: Circle())
        } else {
            Image(systemName: "person.fill")
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(.secondaryText)
                .frame(width: 64, height: 64)
                .overlay {
                    Circle()
                        .strokeBorder(brandGradient, lineWidth: 2)
                }
                .accessibilityHidden(true)
        }
    }

    // MARK: - 统计 / Stats

    /// 两项统计并排，中间一条竖线分隔 / The two stats side by side, split by a vertical rule
    private func statsRow(for user: ProfileUser) -> some View {
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

    // MARK: - 登录按钮 / Sign-in button

    /// 未登录时占统计行的位置，与 `SignInSheet` 的提交按钮同款、同名 /
    /// Takes the stats row's place when signed out; same style and title as the submit button in `SignInSheet`
    private var signInButton: some View {
        Button(action: signIn) {
            Text("Sign In")
                .font(.headline)
                .foregroundStyle(.onBrandGreen)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.glassProminent)
        .controlSize(.large)
        .tint(.brandGreen)
        .padding(16)
    }
}

#Preview("Signed In") {
    ProfileIdentityCardView(user: .mock, signIn: {})
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.appBackground)
}

#Preview("Signed Out") {
    ProfileIdentityCardView(user: nil, signIn: {})
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.appBackground)
}
