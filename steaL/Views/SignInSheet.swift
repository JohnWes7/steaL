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

    @Environment(AuthService.self) private var authService
    @Environment(\.dismiss) private var dismiss

    @State private var mode: Mode = .signIn
    @State private var displayName = ""
    @State private var email = ""
    @State private var password = ""
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
                Section {
                    Picker("Mode", selection: $mode) {
                        ForEach(Mode.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                } footer: {
                    // HIG：先说明登录能换来什么 / HIG: explain what signing in is for before asking
                    Text("Sign in to keep a wishlist of games and see it on all your devices.")
                }

                fieldsSection
            }
            .navigationTitle(mode.rawValue)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbarContent }
        }
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

    // MARK: - 表单 / Form

    private var fieldsSection: some View {
        Section {
            if mode == .createAccount {
                TextField("Display Name", text: $displayName)
                    .textContentType(.name)
            }
            TextField("Email", text: $email)
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            // 注册用 `.newPassword`，系统才会给强密码建议 / `.newPassword` lets the system suggest a strong password
            SecureField("Password", text: $password)
                .textContentType(mode == .signIn ? .password : .newPassword)
        } footer: {
            if mode == .createAccount {
                Text("Password must be at least 6 characters.")
            }
        }
        .disabled(isSubmitting)
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") { dismiss() }
                .disabled(isSubmitting)
        }
        ToolbarItem(placement: .confirmationAction) {
            if isSubmitting {
                ProgressView()
            } else {
                Button(mode.rawValue) { submit() }
                    .disabled(!canSubmit)
            }
        }
    }

    // MARK: - 提交 / Submit

    private func submit() {
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
    SignInSheet()
        .environment(AuthService())
}
