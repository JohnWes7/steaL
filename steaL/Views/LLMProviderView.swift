//
//  LLMProviderView.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import OSLog
import SwiftData
import SwiftUI

/// 服务商页：看 URL 与 key 状态、Edit 改资料、增删模型、删除服务商。
/// The provider page: shows the URL and key status, edits the details, adds and removes models, and deletes the provider.
struct LLMProviderView: View {

    /// 这一页管理的服务商 id。
    /// The id of the provider this page manages.
    ///
    /// 只传 id、页面自己用 `@Query` 查：列表不读 `provider.models`，它不会通知界面从模型那一侧的增删。
    /// Only the id comes in and the page queries for itself, because `provider.models` doesn't notify the view of
    /// changes made from the model's side.
    let providerID: UUID

    /// 这个服务商，最多一个；刚被删除时为空。条件在 `init(providerID:)` 里设置。
    /// This provider, at most one; empty once it has been deleted. The filter is set in `init(providerID:)`.
    @Query private var providers: [LLMProvider]

    /// 这个服务商的模型，按添加顺序。条件和排序在 `init(providerID:)` 里设置。
    /// This provider's models, in the order they were added. The filter and sort are set in `init(providerID:)`.
    @Query private var models: [LLMModel]

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    /// key 状态。
    /// The key status.
    @State private var viewModel = LLMProviderViewModel()

    /// 是否弹出编辑表单。
    /// Whether the edit form is shown.
    @State private var isShowingEditSheet = false

    /// 末尾输入框里正在输入的模型名。
    /// The model name being typed in the last row.
    @State private var newModelName = ""

    /// 是否弹出删除确认。
    /// Whether the delete confirmation is shown.
    @State private var isConfirmingDelete = false

    /// 正在删除，挡住重复点击。
    /// Deleting; repeat taps are ignored.
    @State private var isDeleting = false

    /// 是否弹出删除失败的 alert。
    /// Whether the delete-failure alert is shown.
    @State private var isShowingDeleteError = false

    /// 新增模型的输入框是否聚焦。
    /// Whether the add-model field is focused.
    @FocusState private var isAddFieldFocused: Bool

    /// 为某个服务商建页面，并按它的 id 设置两个查询。
    /// Creates the page for a provider and sets up both queries from its id.
    ///
    /// - Parameter providerID: 服务商的 id / The provider's id.
    init(providerID: UUID) {
        self.providerID = providerID

        let providerPredicate = #Predicate<LLMProvider> { provider in
            provider.id == providerID
        }
        _providers = Query(filter: providerPredicate)

        let modelsPredicate = #Predicate<LLMModel> { model in
            model.provider.id == providerID
        }
        _models = Query(filter: modelsPredicate, sort: \LLMModel.createdAt)
    }

    var body: some View {
        Group {
            if let provider = providers.first {
                providerList(provider)
            } else {
                // 删除后、返回动画中查不到；只画背景，不碰已删除的对象。
                // Nothing is found while popping after a delete, so only the background is drawn.
                Color.appBackground
            }
        }
        // 打开时查一次；表单关掉时 id 变了再查一次（可能刚改了 key）。
        // Checks on appear, and again when the form closes and the id changes (the key may have just changed).
        .task(id: isShowingEditSheet) {
            await viewModel.checkKey(for: providerID)
        }
        .alert("Couldn't Delete Provider", isPresented: $isShowingDeleteError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("The API key couldn't be removed from the Keychain, so nothing was deleted. Try again.")
        }
    }

    // MARK: - 视图片段 / Pieces

    /// 服务商在时的整页。
    /// Returns the whole page while the provider exists.
    ///
    /// - Parameter provider: 查到的服务商 / The provider that was found.
    /// - Returns: 服务商页的列表 / The provider page's list.
    private func providerList(_ provider: LLMProvider) -> some View {
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
                ForEach(models) { model in
                    Text(model.name)
                }
                // 下标和显示用的是同一个 `@Query` 数组，才对得上。
                // The offsets index into the same `@Query` array that is displayed, so they always match.
                .onDelete(perform: deleteModels)

                // 在列表末尾直接输入新增，不弹 alert 输入框。
                // Adds inline at the end of the list instead of an alert text field.
                TextField("Add Model", text: $newModelName, prompt: Text("Add Model ID"))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($isAddFieldFocused)
                    // 有字时回车 = 加完继续，空着回车才收键盘，所以是回车键而不是 ✓。
                    // Return with text adds and continues, and return on empty ends editing, so this is a return key.
                    .submitLabel(.return)
                    .onSubmit(addModel)
            } header: {
                Text("Models")
            } footer: {
                Text("Use the model ID from the provider's documentation, such as deepseek-flash.")
            }

            deleteProviderSection(provider)
        }
        .tint(.brandGreen)
        .scrollContentBackground(.hidden)
        .background(Color.appBackground)
        .navigationTitle(provider.name)
        .navigationBarTitleDisplayMode(.inline)
        // 刚添加、还没有模型时直接聚焦输入框，接着就能加模型。
        // A new provider without models focuses the field right away, ready for the first model.
        .defaultFocus($isAddFieldFocused, models.isEmpty)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("Edit") {
                    isShowingEditSheet = true
                }
            }
        }
        .sheet(isPresented: $isShowingEditSheet) {
            LLMProviderFormSheet(editing: provider)
        }
    }

    /// 最下面的 Delete Provider 一组，先确认再删。
    /// Returns the Delete Provider group at the bottom, which confirms before deleting.
    ///
    /// - Parameter provider: 要删除的服务商 / The provider to delete.
    /// - Returns: Delete Provider 这一组 / The Delete Provider section.
    private func deleteProviderSection(_ provider: LLMProvider) -> some View {
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
                Button("Delete Provider", role: .destructive) {
                    deleteProvider(provider)
                }
            } message: {
                Text("Its models and API key will be removed from this device.")
            }
        }
    }

    /// API Key 行右侧的状态。
    /// The status on the API Key row.
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

    /// 把输入的模型名加进来。
    /// Adds the typed model name.
    ///
    /// 空的、同服务商已有的不加。加完焦点留在输入框，可以接着加。
    /// Blanks and names the provider already has are skipped. Focus stays in the field for the next one.
    private func addModel() {
        let modelName = newModelName.trimmingCharacters(in: .whitespacesAndNewlines)
        newModelName = ""

        guard !modelName.isEmpty else { return }

        let existingNames = models.map { model in
            model.name
        }
        guard !existingNames.contains(modelName) else { return }

        guard let provider = providers.first else { return }

        let model = LLMModel(name: modelName, provider: provider)
        modelContext.insert(model)

        isAddFieldFocused = true
    }

    /// 左滑删除模型。
    /// Deletes the models swiped away.
    ///
    /// 删的正好是选中的模型时，设置经反向关系自动回到内置。
    /// If the chosen model is deleted, the inverse relationship puts the settings back on the built-in model.
    ///
    /// - Parameter offsets: 要删的行在 `models` 里的位置 / The positions of the rows to delete in `models`.
    private func deleteModels(at offsets: IndexSet) {
        for index in offsets {
            let model = models[index]
            modelContext.delete(model)
        }
    }

    // MARK: - 删除服务商 / Deleting the provider

    /// 删除服务商：Keychain 里的 key 和服务商本身，模型随 cascade 一起删。
    /// Deletes the provider: its Keychain key and the provider itself, with its models removed by the cascade.
    ///
    /// 先删 Keychain：失败就什么都不删，两边始终一致。
    /// 先返回再删 SwiftData：少让正在消失的页面去读已删除的对象。
    /// The Keychain goes first: if it fails, nothing is deleted, so both stores stay in step.
    /// Dismissing before the SwiftData delete keeps the leaving page from reading a deleted object.
    ///
    /// - Parameter provider: 要删除的服务商 / The provider to delete.
    private func deleteProvider(_ provider: LLMProvider) {
        guard !isDeleting else { return }

        isDeleting = true

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
            LLMProviderView(providerID: provider.id)
        }
    }
}
