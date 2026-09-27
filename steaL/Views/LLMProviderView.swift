//
//  LLMProviderView.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import OSLog
import SwiftData
import SwiftUI

// 「选」在 AI Model 页，这一页只「管」，同设置 → 无线局域网点 ⓘ 进的网络页（「忽略此网络」也在那页）/
// Picking lives on the AI Model page; this page only manages, like the network page behind ⓘ in Settings → Wi-Fi
/// 服务商页：看 URL 与 key 状态、Edit 改资料、增删模型、删除服务商 /
/// The provider page: shows the URL and key status, edits details, adds and removes models, and deletes the provider
struct LLMProviderView: View {

    /// 这一页管理的服务商 / The provider this page manages
    let provider: LLMProvider

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    /// key 状态 / The key status
    @State private var viewModel = LLMProviderViewModel()

    /// 是否弹出编辑表单 / Whether the edit form is shown
    @State private var isShowingEditSheet = false
    /// 末尾输入框里正在输入的模型名 / The model name being typed in the last row
    @State private var newModelName = ""
    /// 是否弹出删除确认 / Whether the delete confirmation is shown
    @State private var isConfirmingDelete = false
    /// 正在删除，挡住重复点击 / Deleting; ignores repeat taps
    @State private var isDeleting = false
    /// 是否弹出删除失败的 alert / Whether the delete-failure alert is shown
    @State private var isShowingDeleteError = false

    @FocusState private var isAddFieldFocused: Bool

    var body: some View {
        List {
            Section {
                LabeledContent("Base URL", value: provider.baseURL)

                LabeledContent("API Key") {
                    keyStatusText
                }
            } footer: {
                if viewModel.keyState == .notSet {
                    Text("No API key is saved for this provider. Tap Edit to add one.")
                }
            }

            Section {
                // 每行自己带删除按钮、直接拿着这一行的模型；不用 `.onDelete` 的下标，
                // 它是显示时的位置，处理时再算一次列表可能对不上，越界崩溃（2026-09-27 实测）/
                // Each row carries its own delete button holding its model; `.onDelete` offsets are display positions,
                // and recomputing the list in the handler can disagree and crash out of range (seen 2026-09-27)
                ForEach(provider.sortedModels) { model in
                    Text(model.name)
                        .swipeActions {
                            Button("Delete", systemImage: "trash", role: .destructive) {
                                deleteModel(model)
                            }
                        }
                }

                // 列表末尾直接输入新增，同「提醒事项」；不用 alert 输入框（HIG：只在需要输入才能继续时用）/
                // Add inline at the end, like Reminders; no alert text field (HIG keeps those for input needed to proceed)
                TextField("Add Model", text: $newModelName, prompt: Text("Add Model ID"))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($isAddFieldFocused)
                    .submitLabel(.done)
                    .onSubmit(addModel)
            } header: {
                Text("Models")
            } footer: {
                Text("Use the model ID from the provider's documentation, such as deepseek-flash.")
            }

            Section {
                Button("Delete Provider", role: .destructive) {
                    isConfirmingDelete = true
                }
                .disabled(isDeleting)
                .confirmationDialog(
                    "Delete \(provider.name)?",
                    isPresented: $isConfirmingDelete,
                    titleVisibility: .visible
                ) {
                    Button("Delete Provider", role: .destructive, action: deleteProvider)
                } message: {
                    Text("Its models and API key will be removed from this device.")
                }
            }
        }
        .tint(.brandGreen)
        .scrollContentBackground(.hidden)
        .background(Color.appBackground)
        .navigationTitle(provider.name)
        .navigationBarTitleDisplayMode(.inline)
        // 刚添加、还没有模型时直接聚焦输入框，接着就能加模型 / A new provider without models focuses the field right away
        .defaultFocus($isAddFieldFocused, provider.models.isEmpty)
        .toolbar {
            // 同「通讯录」联系人页的 Edit：打开表单改资料 / Like Contacts' Edit: opens the form to change the details
            ToolbarItem(placement: .primaryAction) {
                Button("Edit") {
                    isShowingEditSheet = true
                }
            }
        }
        .sheet(isPresented: $isShowingEditSheet) {
            LLMProviderFormSheet(editing: provider)
        }
        // 打开时查一次；表单关掉时 id 变了再查一次（可能刚改了 key）/
        // Checks on appear, and again when the form closes and the id changes (the key may have just changed)
        .task(id: isShowingEditSheet) {
            await viewModel.checkKey(for: provider.id)
        }
        .alert("Couldn't Delete Provider", isPresented: $isShowingDeleteError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("The API key couldn't be removed from the Keychain, so nothing was deleted. Try again.")
        }
    }

    // MARK: - 视图片段 / Pieces

    /// API Key 行右侧的状态 / The status on the API Key row
    @ViewBuilder
    private var keyStatusText: some View {
        switch viewModel.keyState {
        case .checking:
            ProgressView()
        case .saved:
            Text("Saved")
        case .notSet:
            Text("Not Set")
        case .failed:
            Text("Couldn't Check")
        }
    }

    // MARK: - 模型 / Models

    /// 把输入的模型名加进来；空的、已有的不加。加完焦点留在输入框，可以接着加 /
    /// Adds the typed model name, skipping blanks and duplicates; focus stays in the field for the next one
    private func addModel() {
        let modelName = newModelName.trimmingCharacters(in: .whitespacesAndNewlines)
        newModelName = ""

        guard !modelName.isEmpty else { return }

        let existingNames = provider.models.map { model in
            model.name
        }
        guard !existingNames.contains(modelName) else { return }

        let model = LLMModel(name: modelName, provider: provider)
        modelContext.insert(model)

        isAddFieldFocused = true
    }

    // 删的正好是选中的模型时，设置经反向关系自动回到内置 /
    // If the chosen model is deleted, the inverse relationship puts the settings back on the built-in model
    /// 左滑删除一个模型 / Deletes a model swiped away
    private func deleteModel(_ model: LLMModel) {
        modelContext.delete(model)
    }

    // MARK: - 删除服务商 / Deleting the provider

    // 先删 Keychain：失败就什么都不删，两边始终一致（同表单「存 key 失败就什么都不写」）。
    // 先返回再删 SwiftData：少让正在消失的页面去读已删除的对象 /
    // The Keychain goes first: if it fails nothing is deleted, so both stores stay in step (like the form's save).
    // Dismissing before the SwiftData delete keeps the leaving page from reading a deleted object
    /// 删除服务商：Keychain 的 key、服务商本身，模型随 cascade 一起删 /
    /// Deletes the provider: its Keychain key and the provider itself, with its models removed by the cascade
    private func deleteProvider() {
        guard !isDeleting else { return }

        isDeleting = true
        let providerID = provider.id

        Task {
            defer { isDeleting = false }

            do {
                try await APIKeyStore.delete(for: providerID)
            } catch {
                Logger.settings.error("delete API key failed / 删除 key 失败: \(String(describing: error))")
                isShowingDeleteError = true
                return
            }

            dismiss()
            modelContext.delete(provider)
        }
    }
}

#Preview(traits: .llmProviderSampleData) {
    @Previewable @Query(sort: \LLMProvider.createdAt) var providers: [LLMProvider]

    NavigationStack {
        if let provider = providers.first {
            LLMProviderView(provider: provider)
        }
    }
}
