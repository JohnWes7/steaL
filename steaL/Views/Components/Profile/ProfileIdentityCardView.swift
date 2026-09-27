//
//  ProfileIdentityCardView.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import SwiftUI

/// Profile 身份卡：品牌渐变发丝线、头像、姓名邮箱与底部区域；登录前后是同一张卡，只换头像、文字与底部 /
/// Profile identity card: brand gradient hairline, avatar, name and email, and a bottom area;
/// one card before and after sign-in, swapping only the avatar, text and bottom area.
/// 放在 `List` 的一个 `Section` 里当一行用，底色与圆角由 List 画，和其他分组一致 /
/// Meant as the single row of a `List` `Section`; the List draws its background and corners, matching the other groups
struct ProfileIdentityCardView: View {

    /// 要展示的用户，`nil` 表示未登录 / The user shown; `nil` means signed out
    let user: ProfileUser?

    /// 收藏的游戏数，显示在 Tracking；只在登录后用到。
    /// The number of wishlisted games, shown as Tracking; only used when signed in.
    let trackingCount: Int

    /// 点 Sign In，由页面弹出登录 sheet；只在未登录时用到 / Tapping Sign In; the page presents the sign-in sheet. Only used when signed out
    let signIn: () -> Void

    /// 头像圆环的直径，随 Dynamic Type 放大 / The avatar ring's diameter, scaled with Dynamic Type
    @ScaledMetric(relativeTo: .title) private var avatarSize: CGFloat = 64

    /// 发丝线与头像共用的品牌渐变，全页唯一的装饰元素 /
    /// The brand gradient shared by the hairline and the avatar; the page's single decorative gesture
    private var brandGradient: LinearGradient {
        LinearGradient(colors: [.brandGreen, .brandBlue], startPoint: .leading, endPoint: .trailing)
    }

    var body: some View {
        VStack(spacing: 0) {
            identityRow

            Divider()

            if user != nil {
                statsRow
            } else {
                signInButton
            }
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(rowBackground)
    }

    /// 系统分组行的底色，顶上叠品牌发丝线；发丝线放在行背景里才会被 List 的圆角裁掉 /
    /// The system grouped-row color with the brand hairline on top; the hairline lives in the row background so the List's corners clip it
    private var rowBackground: some View {
        Color(.secondarySystemGroupedBackground)
            .overlay(alignment: .top) {
                brandGradient
                    .frame(height: 2)
            }
    }

    // MARK: - 身份行 / Identity

    /// 头像、姓名与邮箱；未登录时是 Guest 与一句登录的好处（HIG：先说明登录能换来什么）/
    /// Avatar, name and email; signed out, Guest and one line on what signing in is for (HIG: explain the benefit first)
    private var identityRow: some View {
        HStack(spacing: 14) {
            avatar

            VStack(alignment: .leading, spacing: 3) {
                Text(user?.displayName ?? "Guest")
                    .font(.system(.title3, weight: .bold))
                    .foregroundStyle(.primaryText)
                    .lineLimit(1)

                if let user {
                    Text(user.email)
                        .font(.subheadline)
                        .foregroundStyle(.secondaryText)
                        .lineLimit(1)
                } else {
                    Text("Sign in to keep a wishlist on all your devices.")
                        .font(.subheadline)
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

    /// 渐变描边的空心圆环，登录前后同一个；里面登录后是首字母，未登录是人形图标 /
    /// A hollow ring stroked with the gradient, the same before and after sign-in; a monogram inside when signed in, a person glyph when signed out
    private var avatar: some View {
        avatarContent
            .frame(width: avatarSize, height: avatarSize)
            .overlay {
                Circle()
                    .strokeBorder(brandGradient, lineWidth: 2)
            }
    }

    /// 圆环里的内容 / What sits inside the ring
    @ViewBuilder
    private var avatarContent: some View {
        if let user {
            Text(user.initial)
                .font(.system(.title, weight: .bold))
                .foregroundStyle(.primaryText)
        } else {
            Image(systemName: "person.fill")
                .font(.system(.title, weight: .semibold))
                .foregroundStyle(.secondaryText)
                .accessibilityHidden(true)
        }
    }

    // MARK: - 统计 / Stats

    /// 两项统计并排，中间一条竖线分隔；Alerts sent 还没有数据源，先写死 0 /
    /// The two stats side by side, split by a vertical rule; Alerts sent has no data source yet, so it stays 0
    private var statsRow: some View {
        HStack(spacing: 0) {
            stat(value: trackingCount, label: "Tracking", tint: .brandGreen)

            Divider()
                .frame(height: 34)
                .padding(.horizontal, 16)

            stat(value: 0, label: "Alerts sent", tint: .brandBlue)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
    }

    /// 一项统计：数值在上、标签在下，与详情页的 ITAD 指标同一套排版 /
    /// One stat, figure above its label, matching the ITAD metrics on the detail page
    private func stat(value: Int, label: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value.formatted())
                .font(.system(.title3, design: .rounded, weight: .heavy))
                .foregroundStyle(tint)

            Text(label)
                .font(.system(.caption, design: .rounded, weight: .medium))
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
    List {
        Section {
            ProfileIdentityCardView(user: .mock, trackingCount: 12, signIn: {})
        }
    }
    .scrollContentBackground(.hidden)
    .background(Color.appBackground)
}

#Preview("Signed Out") {
    List {
        Section {
            ProfileIdentityCardView(user: nil, trackingCount: 0, signIn: {})
        }
    }
    .scrollContentBackground(.hidden)
    .background(Color.appBackground)
}
