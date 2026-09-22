//
//  SignInSheet.swift
//  steaL
//
//  Created by yijiang on 2026/9/22.
//

import SwiftUI
import FirebaseCore

/// 登录 / 注册 sheet，两种模式在同一个 sheet 里切换 / Sign-in / sign-up sheet; both modes switch in place
struct SignInSheet: View {

    /// 表单模式 / Form mode
    enum Mode: String, CaseIterable, Identifiable {
        case signIn = "Sign In"
        case createAccount = "Create Account"

        var id: Self { self }
    }

    /// 可聚焦的输入框 / Focusable fields
    private enum Field: Hashable {
        case displayName, email, password
    }

    @Environment(AuthService.self) private var authService
    @Environment(\.dismiss) private var dismiss

    @State private var mode: Mode = .signIn
    @State private var displayName = ""
    @State private var email = ""
    @State private var password = ""
    @FocusState private var focusedField: Field?
    /// 请求进行中 / A request is in flight
    @State private var isSubmitting = false
    /// 提交失败的文案，非 nil 时弹 alert / Failure message; shows the alert when non-nil
    @State private var errorMessage: String?

    /// 必填项都填了才能提交 / Submit only once every required field is filled
    private var canSubmit: Bool {
        let hasEmail = !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasName = !displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return hasEmail && !password.isEmpty && (mode == .signIn || hasName)
    }

    var body: some View {
        NavigationStack {
            Form {
                header

                Section {
                    Picker("Mode", selection: $mode.animation()) {
                        ForEach(Mode.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }

                fieldsSection
                submitSection
            }
            // 分区间距收紧，键盘弹起时提交按钮尽量不被挡 / Tighter section spacing keeps the submit button above the keyboard where possible
            .listSectionSpacing(.compact)
            .scrollDismissesKeyboard(.interactively)
            // 打开就能直接输入，省一次点按 / Ready to type on open, saving a tap
            .onAppear { focusedField = .email }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    // iOS 26 的 `.close` 角色显示成系统 xmark 按钮 / The `.close` role renders the system xmark button on iOS 26
                    Button(role: .close) { dismiss() }
                        .disabled(isSubmitting)
                }
            }
        }
        .tint(.brandGreen)
        // 请求没回来前不许下拉关掉 / Don't allow swipe-to-dismiss while a request is in flight
        .interactiveDismissDisabled(isSubmitting)
        .alert(
            "\(mode.rawValue) Failed",
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

    /// HIG：先说明登录能换来什么 / HIG: explain what signing in is for before asking
    private var header: some View {
        Section {
            VStack(spacing: 10) {
                // App Icon 的静态导出，圆角已在图里；改了 `.icon` 要重新导出 / Static export of the app icon, corners baked in; re-export after changing the `.icon`
                Image(.appIcon)
                    .resizable()
                    .frame(width: 64, height: 64)

                Text(mode == .signIn ? "Sign In to steaL" : "Create Your Account")
                    .font(.title.bold())
                    .foregroundStyle(.primaryText)

                Text("Keep a wishlist of games and see it on all your devices.")
                    .font(.subheadline)
                    .foregroundStyle(.secondaryText)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .listRowBackground(Color.clear)
        }
    }

    // MARK: - 表单 / Form

    private var fieldsSection: some View {
        Section {
            if mode == .createAccount {
                TextField("Display Name", text: $displayName)
                    .textContentType(.name)
                    .focused($focusedField, equals: .displayName)
                    .submitLabel(.next)
                    .onSubmit { focusedField = .email }
            }
            TextField("Email", text: $email)
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focusedField, equals: .email)
                .submitLabel(.next)
                .onSubmit { focusedField = .password }
            // 注册用 `.newPassword`，系统才会给强密码建议 / `.newPassword` lets the system suggest a strong password
            SecureField("Password", text: $password)
                .textContentType(mode == .signIn ? .password : .newPassword)
                .focused($focusedField, equals: .password)
                .submitLabel(mode == .signIn ? .go : .join)
                // 键盘上的 Go 直接提交，手指不用离开键盘 / Go on the keyboard submits, so the thumb never leaves it
                .onSubmit { if canSubmit { submit() } }
        } footer: {
            if mode == .createAccount {
                Text("Password must be at least 6 characters.")
            }
        }
        .disabled(isSubmitting)
    }

    /// 提交按钮紧跟在密码下面，填完往下一按就走 / The submit button sits right under the password, so filling in flows straight down to it
    private var submitSection: some View {
        Section {
            Button(action: submit) {
                ZStack {
                    // 两个都占位，切换时按钮不跳高度 / Both keep their space so the button height stays put
                    Text(mode.rawValue).opacity(isSubmitting ? 0 : 1)
                    ProgressView().opacity(isSubmitting ? 1 : 0)
                }
                .font(.headline)
                .foregroundStyle(.onBrandGreen)
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .disabled(!canSubmit || isSubmitting)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
        }
    }

    // MARK: - 提交 / Submit

    private func submit() {
        focusedField = nil
        isSubmitting = true
        Task {
            do {
                switch mode {
                case .signIn:
                    try await authService.signIn(email: email, password: password)
                case .createAccount:
                    try await authService.createAccount(email: email, password: password, displayName: displayName)
                }
                // 成功后 `AuthService.state` 由监听器更新，各页面自己刷新 / The listener updates the state; pages refresh on their own
                dismiss()
            } catch {
                errorMessage = error.localizedDescription
            }
            isSubmitting = false
        }
    }
}

#Preview {
    // 预览不走 `AppDelegate`，直接连真实 Firebase / Previews skip `AppDelegate`, so connect to the real Firebase here
    let _ = { if FirebaseApp.app() == nil { FirebaseApp.configure() } }()
    Color.clear
        .sheet(isPresented: .constant(true)) {
            SignInSheet()
                .environment(AuthService())
        }
}
