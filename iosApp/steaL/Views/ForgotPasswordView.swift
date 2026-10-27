//
//  ForgotPasswordView.swift
//  steaL
//
//  Created by yijiang on 2026/10/3.
//

import SwiftUI
import FirebaseCore
import OSLog

/// 忘记密码页：从登录 sheet 推入，填邮箱发重置链接。
/// The forgot-password page: pushed from the sign-in sheet to send a reset link to an email.
///
/// 在登录 sheet 里推一层，不另开 sheet：HIG 要求同一时间只显示一个 sheet，Apple Account 登录的「Forgot password?」也是在同一个流程里往下走。
/// Pushed inside the sign-in sheet rather than presented as another sheet: HIG allows only one sheet at a time, and Apple Account's "Forgot password?" continues within the same flow.
struct ForgotPasswordView: View {

    /// 页面所处的阶段，同一时间只会是其中一种。
    /// The page's phase; exactly one at a time.
    ///
    /// 发送失败不单独一个 case：失败后页面就是回到 `.editing` 可以再发，只是多弹一个 alert，所以失败文案单独放 `errorMessage`。
    /// Failure has no case of its own: after a failure the page is simply back in `.editing`, plus an alert, so the message lives in `errorMessage`.
    enum Phase {
        /// 填邮箱。
        /// Entering the email.
        case editing
        /// 请求进行中。
        /// A request is in flight.
        case sending
        /// 已发送，显示结果说明。
        /// The link has been sent; the result is shown.
        case sent
    }

    @Environment(AuthService.self) private var authService
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
    /// 页面当前的阶段。
    /// The page's current phase.
    @State private var phase: Phase
    /// 发送失败的文案，非 nil 时弹 alert。
    /// The failure message; shows the alert when non-nil.
    @State private var errorMessage: String?

    /// 用登录页已填的邮箱创建页面。
    /// Creates the page with the email already typed on the sign-in page.
    ///
    /// `phase` 只给预览用，让预览能直接显示某个阶段；App 里不传，用默认值。
    /// `phase` exists for previews to show a given phase directly; the app leaves it at the default.
    ///
    /// - Parameters:
    ///   - email: 登录页的邮箱，可以为空 / The sign-in page's email, possibly empty.
    ///   - phase: 一开始的阶段 / The phase to start in.
    init(email: String, phase: Phase = .editing) {
        _email = State(initialValue: email)
        _phase = State(initialValue: phase)
    }

    /// 请求是否进行中。
    /// Whether a request is in flight.
    private var isSending: Bool {
        phase == .sending
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
            switch phase {
            case .sent:
                // Firebase 开了邮箱枚举保护，没注册的邮箱也不报错，所以只能写「如果账号存在」 /
                // Firebase's email enumeration protection doesn't fail for unregistered emails, so the copy can only say "if an account exists"
                header(
                    systemImage: "envelope",
                    title: "Check Your Email",
                    message: "If an account exists for \(email), you'll get an email with a link to reset your password. Check your spam folder too."
                )

                Section {
                    Button {
                        dismiss()
                    } label: {
                        Text("Back to Sign In")
                            .font(.headline)
                            .foregroundStyle(Color.onBrandGreen)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }
            case .editing, .sending:
                header(
                    systemImage: "lock.rotation",
                    title: "Reset Password",
                    message: "Enter the email for your account and we'll send you a link to reset your password."
                )

                emailSection

                Section {
                    Button(action: send) {
                        HStack(spacing: 8) {
                            Text("Send Reset Link")
                            if isSending {
                                // 单独设 `.regular`，否则继承按钮的 `.large` 把按钮撑高 / Own `.regular` size, or the inherited `.large` stretches the button
                                ProgressView()
                                    .controlSize(.regular)
                                    .tint(.onBrandGreen)
                            }
                        }
                        .font(.headline)
                        // `.onBrandGreen` 只配绿底，禁用时换回灰字 / `.onBrandGreen` only suits the green fill; grey text when disabled
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
        }
        .listSectionSpacing(.compact)
        .scrollDismissesKeyboard(.interactively)
        .onAppear { isEmailFocused = true }
        // 请求没回来前不许下拉关掉，同登录页 / Don't allow swipe-to-dismiss while a request is in flight, like the sign-in page
        .interactiveDismissDisabled(isSending)
        .alert(
            "Couldn't Send Reset Link",
            isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
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
        .disabled(isSending)
    }

    // MARK: - 发送 / Send

    /// 发送重置链接，成功后换成结果说明，失败弹 alert。
    /// Sends the reset link; switches to the result on success and shows an alert on failure.
    ///
    /// 提交中按钮不禁用、保持绿色显示转圈，重复点击由开头的 `guard` 挡掉。
    /// The button stays enabled and green with a spinner while sending; the `guard` ignores repeat taps.
    private func send() {
        // 只有填邮箱阶段才发，挡掉发送中的重复点击 / Send only while editing, ignoring repeat taps during a send
        guard phase == .editing else { return }
        isEmailFocused = false
        phase = .sending
        Task {
            do {
                try await authService.sendPasswordReset(email: email)
                withAnimation {
                    phase = .sent
                }
            } catch {
                Logger.auth.error("Password reset failed: \(String(describing: error))")
                // 回到填邮箱，可以改了再发 / Back to editing so the email can be fixed and resent
                phase = .editing
                errorMessage = error.localizedDescription
            }
        }
    }
}

#Preview("Not Sent") {
    // 预览不走 `AppDelegate`，直接连真实 Firebase / Previews skip `AppDelegate`, so connect to the real Firebase here
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()
    Color.clear
        .sheet(isPresented: .constant(true)) {
            NavigationStack {
                ForgotPasswordView(email: "player@example.com")
            }
            .environment(AuthService())
            .tint(.brandGreen)
        }
}

#Preview("Sent") {
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()
    Color.clear
        .sheet(isPresented: .constant(true)) {
            NavigationStack {
                ForgotPasswordView(email: "player@example.com", phase: .sent)
                    // 换个身份，切换预览时 `@State` 才会用新的初始值 / A distinct identity so `@State` takes the new seed when switching previews
                    .id("sent")
            }
            .environment(AuthService())
            .tint(.brandGreen)
        }
}
