//
//  ProfileUser.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import Foundation

/// Profile 页展示的用户资料与统计；接入 Firebase 后由账号数据填充 /
/// The profile shown on the Profile tab, with its tracking stats; filled from Firebase later
struct ProfileUser {

    /// 显示名 / The display name
    let displayName: String

    /// 邮箱 / The email address
    let email: String

    /// 正在追踪的游戏数 / How many games are being tracked
    let trackingCount: Int

    /// 已发出的降价提醒数 / How many price-drop alerts have been sent
    let alertsSentCount: Int

    /// 头像里显示的首字母 / The monogram shown in the avatar
    var initial: String {
        displayName.first.map(String.init) ?? "?"
    }

    /// 接入 Firebase 前的占位数据 / Placeholder data until Firebase is wired
    static let mock = ProfileUser(
        displayName: "Yijiang Luo",
        email: "yijiang@student.monash.edu",
        trackingCount: 12,
        alertsSentCount: 28
    )
}
