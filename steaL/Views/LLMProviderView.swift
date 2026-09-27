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

    // 只传 id、页面自己用 `@Query` 查，同 Apple 示例 SwiftDataAnimals 的 `AnimalList`（传分类名、按它查动物）。
    // 列表不读 `provider.models`：从模型那一侧建关系或删除模型时，关系数组不会通知界面刷新（2026-09-27 实测）/
    // Only the id comes in and the page queries for itself, like `AnimalList` in Apple's SwiftDataAnimals sample.
    // The list doesn't read `provider.models`: adding or deleting from the model's side never tells the view to refresh
    /// 这一页管理的服务商 id / The id of the provider this page manages
    let providerID: UUID

    // 下面两个查询的条件和排序都在 init 里按 `providerID` 设置 / Both queries get their filter and sort in init from `providerID`

    /// 这个服务商（最多一个）；刚被删除时为空 / This provider (at most one); empty once it has been deleted
    @Query private var providers: [LLMProvider]
    /// 这个服务商的模型，按添加顺序 / This provider's models, in the order they were added
    @Query private var models: [LLMModel]

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
                // 删除后、返回动画中查不到；只画背景，不碰已删除的对象 / Nothing is found while popping after a delete; draw only the background
                Color.appBackground
            }
        }
        // 打开时查一次；表单关掉时 id 变了再查一次（可能刚改了 key）/
        // Checks on appear, and again when the form closes and the id changes (the key may have just changed)
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

    /// 服务商在时的整页 / The whole page while the provider exists
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
                // 下标和显示用的是同一个 `@Query` 数组，同 Apple 示例的 `removeAnimals` /
                // The offsets index into the same `@Query` array that is displayed, like `removeAnimals` in Apple's sample
                .onDelete(perform: deleteModels)

                // 列表末尾直接输入新增，同「提醒事项」；不用 alert 输入框（HIG：只在需要输入才能继续时用）/
                // Add inline at the end, like Reminders; no alert text field (HIG keeps those for input needed to proceed)
                TextField("Add Model", text: $newModelName, prompt: Text("Add Model ID"))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($isAddFieldFocused)
                    // 有字时回车 = 加完继续，空着回车才收键盘，同「提醒事项」；所以是回车键而不是 ✓ /
                    // Return with text adds and continues, return on empty ends editing, like Reminders; so a return key, not a checkmark
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
        // 刚添加、还没有模型时直接聚焦输入框，接着就能加模型 / A new provider without models focuses the field right away
        .defaultFocus($isAddFieldFocused, models.isEmpty)
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
    }

    /// 最下面的 Delete Provider 一组，先确认再删 / The Delete Provider group at the bottom, confirmed before deleting
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

        let existingNames = models.map { model in
            model.name
        }
        guard !existingNames.contains(modelName) else { return }

        guard let provider = providers.first else { return }

        let model = LLMModel(name: modelName, provider: provider)
        modelContext.insert(model)

        isAddFieldFocused = true
    }

    // 删的正好是选中的模型时，设置经反向关系自动回到内置 /
    // If the chosen model is deleted, the inverse relationship puts the settings back on the built-in model
    /// 左滑删除 / Swipe to delete
    private func deleteModels(at offsets: IndexSet) {
        for index in offsets {
            let model = models[index]
            modelContext.delete(model)
        }
    }

    // MARK: - 删除服务商 / Deleting the provider

    // 先删 Keychain：失败就什么都不删，两边始终一致（同表单「存 key 失败就什么都不写」）。
    // 先返回再删 SwiftData：少让正在消失的页面去读已删除的对象 /
    // The Keychain goes first: if it fails nothing is deleted, so both stores stay in step (like the form's save).
    // Dismissing before the SwiftData delete keeps the leaving page from reading a deleted object
    /// 删除服务商：Keychain 的 key、服务商本身，模型随 cascade 一起删 /
    /// Deletes the provider: its Keychain key and the provider itself, with its models removed by the cascade
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
