//
//  LLMProviderFormSheet.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import OSLog
import SwiftData
import SwiftUI

// 新增与编辑共用一张表单，同 Apple SwiftData 示例的 `AnimalEditor`：`provider` 为 `nil` 是新增。
// 输入先放在自己的 `@State` 里，点确认才写回，Cancel 什么都不改 /
// One form for adding and editing, like the `AnimalEditor` in Apple's SwiftData sample: a `nil` provider means adding.
// Input stays in the form's own `@State` and is written back only on confirm, so Cancel changes nothing
/// 新增 / 编辑服务商的表单：Name、Base URL、API Key / The form for adding or editing a provider: name, base URL and API key
struct LLMProviderFormSheet: View {

    /// 表单里可聚焦的输入框 / The form's focusable fields
    private enum Field {
        case name
        case baseURL
        case apiKey
    }

    /// 要编辑的服务商；`nil` 是新增 / The provider being edited; `nil` when adding
    private let provider: LLMProvider?

    // 用 `@Binding` 而不是 `onAdd` 回调：sheet 自己保存、自己关，只把结果写回弹出它的页面 /
    // A `@Binding` rather than an `onAdd` callback: the sheet saves and dismisses itself and only hands the result back
    /// 新增成功后写入新服务商的 id，弹出它的页面据此推入；编辑时不用 /
    /// Receives the new provider's id once added so the presenting page can push it; unused when editing
    @Binding private var addedProviderID: UUID?

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    // 下面三项只在 init 里赋一次初值，之后归表单自己管 / The next three are seeded once in init and then owned by the form

    /// 名字输入 / The name being typed
    @State private var name: String
    /// base URL 输入 / The base URL being typed
    @State private var baseURL: String
    /// API key 输入；编辑时留空表示不改 / The API key being typed; left blank when editing to keep the current key
    @State private var apiKey: String

    /// 正在保存（写 Keychain），挡住重复点击和下拉关闭 / Saving to the Keychain; blocks repeat taps and swipe-to-dismiss
    @State private var isSaving = false
    /// 是否弹出保存失败的 alert / Whether the save-failure alert is shown
    @State private var isShowingSaveError = false

    @FocusState private var focusedField: Field?

    // MARK: - 初始化 / Init

    /// 新增服务商 / Adds a provider
    init(addedProviderID: Binding<UUID?>) {
        self.provider = nil
        self._addedProviderID = addedProviderID
        self.name = ""
        self.baseURL = ""
        self.apiKey = ""
    }

    /// 编辑已有服务商；key 不预填（不为显示去读 Keychain）/ Edits a provider; the key is not prefilled, so the Keychain is never read for display
    init(editing provider: LLMProvider) {
        self.provider = provider
        self._addedProviderID = .constant(nil)
        self.name = provider.name
        self.baseURL = provider.baseURL
        self.apiKey = ""
    }

    // MARK: - 视图 / Body

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    // 左边标签、右边输入框，提示写 Required，同系统设置的「添加 VPN 配置」/
                    // Label on the leading side and a "Required" prompt in the field, like Settings' Add VPN Configuration
                    LabeledContent("Name") {
                        TextField("Name", text: $name, prompt: Text("Required"))
                            .focused($focusedField, equals: .name)
                            .submitLabel(.next)
                            .onSubmit {
                                focusedField = .baseURL
                            }
                    }

                    LabeledContent("Base URL") {
                        // 字面量会当本地化 key 按 Markdown 解析，网址被识别成蓝色链接；示例网址也不用翻译 /
                        // A literal is a localization key parsed as Markdown, which turns the URL into a blue link; an example URL needs no translation
                        TextField("Base URL", text: $baseURL, prompt: Text(verbatim: "https://api.example.com"))
                            .keyboardType(.URL)
                            .textContentType(.URL)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .focused($focusedField, equals: .baseURL)
                            .submitLabel(.next)
                            .onSubmit {
                                focusedField = .apiKey
                            }
                    }
                } footer: {
                    Text("Any service that supports the OpenAI Chat Completions API. The URL starts with https://, and /chat/completions is added for you.")
                }

                Section {
                    LabeledContent("API Key") {
                        SecureField("API Key", text: $apiKey, prompt: Text(apiKeyPrompt))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .focused($focusedField, equals: .apiKey)
                            .submitLabel(.done)
                            .onSubmit(save)
                    }
                } footer: {
                    Text(apiKeyFooter)
                }
            }
            .disabled(isSaving)
            .defaultFocus($focusedField, initialFocus)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    // 标题给 VoiceOver 和溢出菜单用，工具栏里只显示图标 / The title serves VoiceOver and overflow; the bar shows the icon
                    Button("Cancel", systemImage: "xmark", role: .cancel) {
                        dismiss()
                    }
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button(confirmTitle, systemImage: "checkmark", role: .confirm, action: save)
                        .disabled(!canSave || isSaving)
                }
            }
            .alert("Couldn't Save API Key", isPresented: $isShowingSaveError) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("The key couldn't be stored in the Keychain. Try again.")
            }
        }
        // 填了东西就不许下拉关掉，免得输入的 key 丢了 / Once something is typed, swipe-to-dismiss is off so the input isn't lost
        .interactiveDismissDisabled(hasChanges || isSaving)
    }

    // MARK: - 文案 / Text

    /// 导航栏标题 / The navigation title
    private var title: String {
        if provider == nil {
            return "Add Provider"
        }
        return "Edit Provider"
    }

    /// 确认按钮的标题 / The confirm button's title
    private var confirmTitle: String {
        if provider == nil {
            return "Add"
        }
        return "Save"
    }

    /// API key 输入框的提示 / The API key field's prompt
    private var apiKeyPrompt: String {
        if provider == nil {
            return "Required"
        }
        return "Unchanged"
    }

    /// API key 分组的说明 / The API key section's footer
    private var apiKeyFooter: String {
        if provider == nil {
            return "Stored in the Keychain."
        }
        return "Leave blank to keep the current key."
    }

    /// 新增时先聚焦名字，编辑时不自动弹键盘 / Focuses the name when adding; editing doesn't raise the keyboard
    private var initialFocus: Field? {
        if provider == nil {
            return .name
        }
        return nil
    }

    // MARK: - 校验 / Validation

    // 首尾空白（尤其是粘贴 key 带进来的换行）都去掉再用 / Leading and trailing whitespace, such as a pasted newline, is dropped before use

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var trimmedBaseURL: String {
        baseURL.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var trimmedAPIKey: String {
        apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // 只收 https：App Transport Security 默认拦下明文 http 请求 /
    // HTTPS only: App Transport Security blocks plain http requests by default
    /// base URL 是不是完整的 https 地址 / Whether the base URL is a complete https address
    private var isBaseURLValid: Bool {
        guard let url = URL(string: trimmedBaseURL) else { return false }

        let scheme = url.scheme?.lowercased()
        let host = url.host()
        return scheme == "https" && host != nil
    }

    /// 能不能确认：名字、URL 必填；新增时 key 也必填，编辑时留空 = 不改 /
    /// Whether confirm is allowed: name and URL are required; the key is required when adding, and blank keeps it when editing
    private var canSave: Bool {
        let hasName = !trimmedName.isEmpty
        let hasKey = !trimmedAPIKey.isEmpty
        let isEditing = provider != nil
        let hasRequiredKey = hasKey || isEditing
        return hasName && isBaseURLValid && hasRequiredKey
    }

    /// 和打开时相比有没有改动 / Whether anything changed since the form opened
    private var hasChanges: Bool {
        let hasKey = !apiKey.isEmpty

        if let provider {
            let nameChanged = name != provider.name
            let baseURLChanged = baseURL != provider.baseURL
            return nameChanged || baseURLChanged || hasKey
        }

        let hasName = !name.isEmpty
        let hasBaseURL = !baseURL.isEmpty
        return hasName || hasBaseURL || hasKey
    }

    // MARK: - 保存 / Saving

    /// 确认：新增或更新，成功后关掉 sheet / Confirms: adds or updates, then dismisses the sheet
    private func save() {
        guard canSave, !isSaving else { return }

        isSaving = true

        Task {
            defer { isSaving = false }

            do {
                if let provider {
                    try await update(provider)
                } else {
                    try await add()
                }
                dismiss()
            } catch {
                Logger.settings.error("save LLM provider failed / 保存服务商失败: \(String(describing: error))")
                isShowingSaveError = true
            }
        }
    }

    // 先存 key 再插入：Keychain 失败就什么都不插入，不会留下没有 key 的服务商 /
    // The key is saved before inserting: if the Keychain fails nothing is inserted, so no provider is left without a key
    /// 新建服务商并把它交回弹出页 / Creates the provider and hands it back to the presenting page
    private func add() async throws {
        let apiKeyToSave = trimmedAPIKey
        let newProvider = LLMProvider(name: trimmedName, baseURL: trimmedBaseURL)

        try await APIKeyStore.save(apiKeyToSave, for: newProvider.id)

        modelContext.insert(newProvider)
        addedProviderID = newProvider.id
    }

    // key 留空就不动 Keychain；key 存失败时名字和 URL 也不改 /
    // A blank key leaves the Keychain alone; if saving the key fails, the name and URL stay unchanged too
    /// 把改动写回已有服务商 / Writes the changes back to an existing provider
    private func update(_ provider: LLMProvider) async throws {
        let newName = trimmedName
        let newBaseURL = trimmedBaseURL
        let newAPIKey = trimmedAPIKey

        if !newAPIKey.isEmpty {
            try await APIKeyStore.save(newAPIKey, for: provider.id)
        }

        provider.name = newName
        provider.baseURL = newBaseURL
    }
}

#Preview("Add", traits: .llmProviderSampleData) {
    LLMProviderFormSheet(addedProviderID: .constant(nil))
}

#Preview("Edit", traits: .llmProviderSampleData) {
    @Previewable @Query(sort: \LLMProvider.createdAt) var providers: [LLMProvider]

    if let provider = providers.first {
        LLMProviderFormSheet(editing: provider)
    }
}
