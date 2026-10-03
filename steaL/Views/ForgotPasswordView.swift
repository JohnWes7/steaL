//
//  ForgotPasswordView.swift
//  steaL
//
//  Created by yijiang on 2026/10/3.
//

import SwiftUI

/// 忘记密码页：从登录 sheet 推入，填邮箱发重置链接。
/// The forgot-password page: pushed from the sign-in sheet to send a reset link to an email.
///
/// 在登录 sheet 里推一层，不另开 sheet：HIG 要求同一时间只显示一个 sheet，Apple Account 登录的「Forgot password?」也是在同一个流程里往下走。
/// Pushed inside the sign-in sheet rather than presented as another sheet: HIG allows only one sheet at a time, and Apple Account's "Forgot password?" continues within the same flow.
struct ForgotPasswordView: View {

    @Environment(\.dismiss) private var dismiss

    /// 要发重置链接的邮箱。
    /// The email to send the reset link to.
    ///
    /// 只在推入时用登录页已填的邮箱做一次初始值，之后两边各改各的。
    /// Seeded once from the sign-in page's email when pushed; the two fields are independent afterwards.
    @State private var email: String
    /// 邮箱框是否聚焦。
    /// Whether the email field is focused.
    @FocusState private var isEmailFocused: Bool
    /// 是否已发送，发送后换成结果说明。
    /// Whether the link has been sent; the page switches to the result once it has.
    @State private var didSend = false

    /// 用登录页已填的邮箱创建页面。
    /// Creates the page with the email already typed on the sign-in page.
    ///
    /// `didSend` 只给预览用，让预览能直接显示已发送状态；App 里不传，用默认值。
    /// `didSend` exists for previews to show the sent state directly; the app leaves it at the default.
    ///
    /// - Parameters:
    ///   - email: 登录页的邮箱，可以为空 / The sign-in page's email, possibly empty.
    ///   - didSend: 是否一开始就显示已发送 / Whether to start in the sent state.
    init(email: String, didSend: Bool = false) {
        _email = State(initialValue: email)
        _didSend = State(initialValue: didSend)
    }

    /// 邮箱填了才能发送。
    /// Sending is allowed only once an email is entered.
    private var canSend: Bool {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmedEmail.isEmpty
    }

    var body: some View {
        // 不设导航栏标题，标题写在内容顶部，和登录页一致 / No bar title; the title sits in the content, matching the sign-in page
        if didSend {
            sentView
        } else {
            form
        }
    }

    // MARK: - 表单 / Form

    /// 说明 + 邮箱 + 发送按钮。
    /// The explanation, email field and send button.
    private var form: some View {
        Form {
            header

            Section {
                TextField("Email", text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($isEmailFocused)
                    .submitLabel(.send)
                    .onSubmit { if canSend { send() } }
            }

            sendSection
        }
        .listSectionSpacing(.compact)
        .scrollDismissesKeyboard(.interactively)
        .onAppear { isEmailFocused = true }
    }

    /// 图标、标题和一句说明。
    /// The icon, title and one-line explanation.
    private var header: some View {
        Section {
            VStack(spacing: 10) {
                Image(systemName: "lock.rotation")
                    .font(.system(.largeTitle, weight: .semibold))
                    .foregroundStyle(.brandGreen)

                Text("Reset Password")
                    .font(.title.bold())
                    .foregroundStyle(.primaryText)

                Text("Enter the email for your account and we'll send you a link to reset your password.")
                    .font(.subheadline)
                    .foregroundStyle(.secondaryText)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .listRowBackground(Color.clear)
        }
    }

    /// 发送按钮，样式同登录页的提交按钮。
    /// The send button, styled like the sign-in page's submit button.
    private var sendSection: some View {
        Section {
            Button(action: send) {
                Text("Send Reset Link")
                    .font(.headline)
                    .foregroundStyle(canSend ? Color.onBrandGreen : Color.secondary)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(!canSend)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
        }
    }

    // MARK: - 已发送 / Sent

    /// 发送后的说明，不说账号是否存在。
    /// The result after sending, without revealing whether the account exists.
    ///
    /// Firebase 开了邮箱枚举保护，邮箱没注册也不报错，所以只能写「如果账号存在」。
    /// Firebase's email enumeration protection doesn't fail for unregistered emails, so the copy can only say "if an account exists".
    private var sentView: some View {
        ContentUnavailableView {
            Label("Check Your Email", systemImage: "envelope")
        } description: {
            Text("If an account exists for \(email), you'll get an email with a link to reset your password. Check your spam folder too.")
        } actions: {
            Button("Back to Sign In") { dismiss() }
                .buttonStyle(.borderedProminent)
                .foregroundStyle(Color.onBrandGreen)
        }
    }

    // MARK: - 发送 / Send

    /// 发送重置链接（目前只切换到结果，没接 Firebase）。
    /// Sends the reset link (for now only switches to the result; not wired to Firebase).
    private func send() {
        isEmailFocused = false
        // TODO: 接 `AuthService.sendPasswordReset(email:)` / Wire up `AuthService.sendPasswordReset(email:)`
        withAnimation {
            didSend = true
        }
    }
}

#Preview("Not Sent") {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            NavigationStack {
                ForgotPasswordView(email: "player@example.com")
            }
            .tint(.brandGreen)
        }
}

#Preview("Sent") {
    Color.clear
        .sheet(isPresented: .constant(true)) {
            NavigationStack {
                ForgotPasswordView(email: "player@example.com", didSend: true)
            }
            .tint(.brandGreen)
        }
}
