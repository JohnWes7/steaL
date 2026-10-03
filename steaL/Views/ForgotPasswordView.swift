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

    /// 发送前后是同一个 `Form`，只换头部文字、邮箱框和主按钮，排版和主按钮样式不变。
    /// Before and after sending share one `Form`: only the header text, email field and main button change, so the layout and main button style stay the same.
    ///
    /// 不用 `ContentUnavailableView` 显示发送结果：它是给内容不可用（空列表、无结果）用的，而且小胶囊按钮和登录页的整宽主按钮不一致。
    /// The result doesn't use `ContentUnavailableView`: it's meant for unavailable content (empty lists, no results), and its small capsule button doesn't match the sign-in page's full-width button.
    var body: some View {
        // 不设导航栏标题，标题写在内容顶部，和登录页一致 / No bar title; the title sits in the content, matching the sign-in page
        Form {
            if didSend {
                // Firebase 开了邮箱枚举保护，没注册的邮箱也不报错，所以只能写「如果账号存在」 /
                // Firebase's email enumeration protection doesn't fail for unregistered emails, so the copy can only say "if an account exists"
                header(
                    systemImage: "envelope",
                    title: "Check Your Email",
                    message: "If an account exists for \(email), you'll get an email with a link to reset your password. Check your spam folder too."
                )

                mainButton("Back to Sign In", isEnabled: true) {
                    dismiss()
                }
            } else {
                header(
                    systemImage: "lock.rotation",
                    title: "Reset Password",
                    message: "Enter the email for your account and we'll send you a link to reset your password."
                )

                emailSection

                mainButton("Send Reset Link", isEnabled: canSend, action: send)
            }
        }
        .listSectionSpacing(.compact)
        .scrollDismissesKeyboard(.interactively)
        .onAppear { isEmailFocused = true }
    }

    // MARK: - 头部 / Header

    /// 图标、标题和一句说明，排版同登录页。
    /// The icon, title and one-line explanation, laid out like the sign-in page.
    ///
    /// - Parameters:
    ///   - systemImage: SF Symbol 名字 / The SF Symbol name.
    ///   - title: 大标题 / The large title.
    ///   - message: 标题下面的说明 / The explanation under the title.
    private func header(systemImage: String, title: LocalizedStringKey, message: LocalizedStringKey) -> some View {
        Section {
            VStack(spacing: 10) {
                Image(systemName: systemImage)
                    .font(.system(.largeTitle, weight: .semibold))
                    .foregroundStyle(.brandGreen)

                Text(title)
                    .font(.title.bold())
                    .foregroundStyle(.primaryText)

                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.secondaryText)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .listRowBackground(Color.clear)
        }
    }

    // MARK: - 表单 / Form

    /// 邮箱输入框，只在发送前显示。
    /// The email field, shown only before sending.
    private var emailSection: some View {
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
    }

    /// 整宽的主按钮，样式同登录页的提交按钮。
    /// A full-width main button, styled like the sign-in page's submit button.
    ///
    /// - Parameters:
    ///   - title: 按钮文字 / The button title.
    ///   - isEnabled: 能不能点 / Whether the button can be tapped.
    ///   - action: 点了做什么 / What tapping does.
    private func mainButton(_ title: LocalizedStringKey, isEnabled: Bool, action: @escaping () -> Void) -> some View {
        Section {
            Button(action: action) {
                Text(title)
                    .font(.headline)
                    // `.onBrandGreen` 只配绿底，禁用时换回灰字 / `.onBrandGreen` only suits the green fill; grey text when disabled
                    .foregroundStyle(isEnabled ? Color.onBrandGreen : Color.secondary)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(!isEnabled)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
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
                    // 换个身份，切换预览时 `@State` 才会用新的初始值 / A distinct identity so `@State` takes the new seed when switching previews
                    .id("sent")
            }
            .tint(.brandGreen)
        }
}
