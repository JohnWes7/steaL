//
//  AuthService.swift
//  steaL
//
//  Created by yijiang on 2026/9/22.
//

import Foundation
import FirebaseAuth
import Observation


@MainActor
@Observable
final class AuthService {

    /// 显示名称长度上限 / Display name length limit
    private static let displayNameLimit = 20
    /// 密码长度下限 / Password length minimum
    private static let passwordMinimum = 6

    /// state：当前登录状态
    private(set) var state: AuthState = .checking

    /// 当前用户
    var user: FirebaseAuth.User? {
        if case .signedIn(let user) = state { user } else { nil }
    }

    /// Firebase Auth 实例 / Firebase Auth instance
    @ObservationIgnored private let auth: Auth
    /// 监听器句柄 / Listener handle, must be held or it will be released and no callbacks will be received
    @ObservationIgnored private var authListener: AuthStateDidChangeListenerHandle?

    init(auth: Auth = .auth()) {
        self.auth = auth
        // 注册一个监听器，监听用户登录状态的变化。
        authListener = auth.addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in
                // 登录、注销或 token 变化时都会被回调
                if let user {
                    self?.state = .signedIn(user)
                } else {
                    self?.state = .signedOut
                }
            }
        }
    }

    // 登录方法。使用 async/throws 异步执行，出错时向上抛出异常。
    func signIn(email: String, password: String) async throws {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        
        // 校验：邮箱或密码任一为空。按钮在 UI 上已禁用，这里只是兜底。
        guard !cleanEmail.isEmpty, !password.isEmpty else {
            throw AuthError.missingFields
        }

        // 调用 Firebase 的邮箱+密码登录，返回值（用户对象）此处无需使用，故用 _ 忽略。
        _ = try await auth.signIn(withEmail: cleanEmail, password: password)
    }

    // 注册（创建账号）方法。异步执行，出错时抛出异常。
    func createAccount(email: String, password: String, displayName: String) async throws {
        let cleanedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanedName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)

        // 校验：邮箱、密码、显示名称都不能为空。按钮在 UI 上已禁用，这里只是兜底。
        guard !cleanedEmail.isEmpty, !password.isEmpty, !cleanedName.isEmpty else {
            throw AuthError.missingFields
        }

        // 校验：显示名称长度不能超过 displayNameLimit（20 个字符）。
        guard cleanedName.count <= AuthService.displayNameLimit else {
            throw AuthError.displayNameTooLong(limit: AuthService.displayNameLimit)
        }

        // 校验：密码长度至少为 passwordMinimum（6 位）。
        guard password.count >= AuthService.passwordMinimum else {
            throw AuthError.passwordTooShort(minimum: AuthService.passwordMinimum)
        }

        // 在 Firebase Auth 中创建新用户，返回 AuthDataResult（其中包含 user）。
        let result = try await auth.createUser(withEmail: cleanedEmail, password: password)
        // 创建用于修改用户个人资料的请求对象。
        let request = result.user.createProfileChangeRequest()
        request.displayName = cleanedName

        // 显示名与邮箱都存在 Auth 里，不另写 Firestore 的 users 文档，避免两份数据要同步。
        try await request.commitChanges()

        // `User` 是 class，显示名是原地改的，Observation 看不到；重新赋一次 `state` 让页面读到新名字 /
        // `User` is a class and the name changes in place, which Observation can't see; reassign `state` so views pick up the new name
        state = .signedIn(result.user)
    }

    func signOut() throws {
        try auth.signOut()
    }
}

/// 登录状态 / Authentication state
enum AuthState {
    /// 启动时等 Firebase 第一次回调 / Waiting for Firebase's first callback on launch
    case checking
    /// 未登录 / Signed out
    case signedOut
    /// 已登录 / Signed in
    case signedIn(FirebaseAuth.User)
}

/// 认证流程在提交时才能发现的错误 / Auth errors only detectable on submit
enum AuthError: Error, LocalizedError {
    /// 必填项为空（UI 已禁用按钮，兜底用）/ A required field is empty (fallback; the UI disables the button)
    case missingFields
    /// 显示名超长 / Display name is too long
    case displayNameTooLong(limit: Int)
    /// 密码太短 / Password is too short
    case passwordTooShort(minimum: Int)

    /// 面向用户 / For the UI
    var errorDescription: String? {
        switch self {
        case .missingFields:
            return "Please fill in every field."
        case .displayNameTooLong(let limit):
            return "Display name must be at most \(limit) characters."
        case .passwordTooShort(let minimum):
            return "Password must be at least \(minimum) characters."
        }
    }
}
