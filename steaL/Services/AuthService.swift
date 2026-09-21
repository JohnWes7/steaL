//
//  AuthService.swift
//  steaL
//
//  Created by yijiang on 2026/9/22.
//

// 引入必要的系统与第三方框架
import Foundation  // 基础功能，例如字符串处理、集合等
import FirebaseAuth  // 用于处理用户注册、登录、登出等身份认证功能
import Observation  // 提供 @Observable 宏，属性变化时自动通知读取它的 SwiftUI 视图

// @MainActor：标记整个类只能运行在主线程（主执行器）上，
// 这样所有 UI 相关的状态更新都是线程安全的，避免在后台线程操作 UI 造成崩溃。
@MainActor
@Observable
final class AuthService {
    // 显示名称的最大允许长度（静态常量，全类共享，不随实例变化）
    private static let displayNameLimit = 60
    // 密码的最小长度，与 Firebase Auth 的下限一致
    private static let passwordMinimum = 6

    // @Observable 会自动追踪所有存储属性，不需要再逐个写 @Published。
    // private(set)：外部只能读取，不能直接修改，只能通过类内部的方法来更新。
    // user：保存当前登录的 Firebase 用户对象；nil 表示尚未登录。
    private(set) var user: FirebaseAuth.User?
    // isCheckingAuthentication：表示是否仍在检查登录状态（例如应用启动时的自动登录检测）。
    // 初始为 true，当完成状态检测后会被设置为 false。
    private(set) var isCheckingAuthentication = true

    // 私有属性：Firebase 的 Auth 实例，用于执行登录、注册等认证操作。
    // 下面两个都不驱动 UI，用 @ObservationIgnored 排除在追踪之外。
    @ObservationIgnored private let auth: Auth
    // 保存身份认证状态变化的监听器句柄，用于后续移除监听。
    @ObservationIgnored private var authListener: AuthStateDidChangeListenerHandle?

    // 初始化方法。参数提供默认值，因此可以在不传参的情况下直接使用 AuthService()。
    // 也方便在测试时注入模拟的 Auth 实例。
    init(auth: Auth = .auth()) {
        self.auth = auth
        // 注册一个监听器，监听用户登录状态的变化。
        // [weak self]：弱引用 self，避免闭包持有导致循环引用（内存泄漏）。
        authListener = auth.addStateDidChangeListener { [weak self] _, user in
            // 将回调切换到主线程执行（因为类是 @MainActor）。
            Task { @MainActor in
                // 更新当前用户对象；登录、注销或 token 变化时都会被回调。
                self?.user = user
                // 收到回调说明已完成身份状态的检测，将标志位设为 false。
                self?.isCheckingAuthentication = false
            }
        }
    }

    // 登录方法。使用 async/throws 异步执行，出错时向上抛出异常。
    func signIn(email: String, password: String) async throws {
        // 去除邮箱首尾的空白字符与换行符，避免用户误输入空格导致校验失败。
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
        // 对邮箱和显示名称做同样的去空格处理。
        let cleanedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanedName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)

        // 校验：邮箱、密码、显示名称都不能为空。按钮在 UI 上已禁用，这里只是兜底。
        guard !cleanedEmail.isEmpty, !password.isEmpty, !cleanedName.isEmpty else {
            throw AuthError.missingFields
        }

        // 校验：显示名称长度不能超过 displayNameLimit（60 个字符）。
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
        // 设置用户资料中的显示名称。
        request.displayName = cleanedName
        // 提交修改，将显示名称保存到 Firebase Auth 的用户资料中。
        // 显示名与邮箱都存在 Auth 里，不另写 Firestore 的 users 文档，避免两份数据要同步。
        try await request.commitChanges()
    }

    func signOut() throws {
        try auth.signOut()
    }
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
