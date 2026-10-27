//
//  ProfileUser.swift
//  steaL
//
//  Created by yijiang on 2026/9/16.
//

import FirebaseAuth
import Foundation

//WWDC20「Data Essentials in SwiftUI」

/// 身份卡要的最少数据：名字与邮箱。在页面里由 Auth 账号转换，卡片不依赖 Firebase，预览也能造样例 /
/// The minimum the identity card needs: name and email. The page converts the Auth account into it,
/// so the card doesn't depend on Firebase and previews can build samples (WWDC20 "Structure your app for SwiftUI previews")
struct ProfileUser {

    /// 显示名 / The display name
    let displayName: String

    /// 邮箱 / The email address
    let email: String

    /// 头像里显示的首字母 / The monogram shown in the avatar
    var initial: String {
        displayName.first.map(String.init) ?? "?"
    }

    /// 预览用的样例数据 / Sample data for previews
    static let mock = ProfileUser(
        displayName: "Yijiang Luo",
        email: "yijiang@student.monash.edu"
    )
}

// 写在 extension 里，结构体保留自动生成的成员初始化器 /
// Declared in an extension so the struct keeps its synthesized memberwise initializer
extension ProfileUser {

    /// 从 Auth 账号取名字与邮箱 / Name and email from the Auth account
    init(user: FirebaseAuth.User) {
        let email = user.email ?? ""
        // 注册时显示名是第二步才写入的，没有时用邮箱 @ 前面那段 /
        // The display name is written in a second step at sign-up, so fall back to the part of the email before the @
        let emailName = email.split(separator: "@").first.map(String.init)
        self.init(displayName: user.displayName ?? emailName ?? "steaL User", email: email)
    }
}
