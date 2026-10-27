//
//  AIModelSettingsView.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import SwiftData
import SwiftUI

/// AI Model 页：从 Profile 的 AI Model 行推入，选用哪个模型。
/// The AI Model page, pushed from Profile's AI Model row, for choosing which model to use.
///
/// 内置模型与各服务商的模型按组列出，整页只有一个勾；最后是服务商列表。
/// 这一页只负责「选」，服务商的编辑、加模型、删除在 `LLMProviderView`。
/// The built-in model and every provider's models are listed in groups that share one checkmark,
/// followed by the list of providers. This page only picks; editing a provider, adding models and deleting
/// live in `LLMProviderView`.
struct AIModelSettingsView: View {

    /// 用户设置单例行，选中的模型存在它的 `llmModel`。
    /// The singleton settings row; the chosen model lives in its `llmModel`.
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    /// 用户添加的服务商，按添加顺序。
    /// The user's providers, in the order they were added.
    @Query(sort: \LLMProvider.createdAt)
    private var providers: [LLMProvider]

    /// 所有服务商的模型，按添加顺序。
    /// Every provider's models, in the order they were added.
    ///
    /// 分组和计数读这个查询，不读 `provider.models`：从模型那一侧增删时，关系数组不会通知界面刷新。
    /// Grouping and counts read this query rather than `provider.models`, which doesn't notify the view of changes
    /// made from the model's side.
    @Query(sort: \LLMModel.createdAt)
    private var models: [LLMModel]

    /// 是否弹出 Add Provider 表单。
    /// Whether the Add Provider form is shown.
    @State private var isShowingAddProvider = false

    /// 要推入其页面的服务商 id：点行，或表单刚添加的。
    /// The id of the provider whose page to push: a tapped row, or the one the form just added.
    ///
    /// 点服务商行和「添加后推入」共用这一个状态、一个 `.navigationDestination(item:)`。
    /// 行不能再用闭包式 `NavigationLink { … }`：两种推入抢导航栈的同一个位置，整个 App 会卡死。
    /// Tapping a provider row and pushing after adding share this one state and one `.navigationDestination(item:)`.
    /// Rows must not also use a closure `NavigationLink { … }`: the two fight over the same stack slot and hang the app.
    @State private var openedProviderID: UUID?

    var body: some View {
        List {
            Section("Built-in") {
                modelRow(name: Config.llmModel, model: nil)
            }

            ForEach(providers) { provider in
                let providerModels = models(of: provider)

                Section(provider.name) {
                    if providerModels.isEmpty {
                        // 没有可选的也留着这一组，让用户看到这个服务商还没加模型。
                        // Keeps the group so people can see the provider has no models yet.
                        Text("No Models")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(providerModels) { model in
                            modelRow(name: model.name, model: model)
                        }
                    }
                }
            }

            Section {
                ForEach(providers) { provider in
                    providerRow(provider)
                }

                Button("Add Provider…") {
                    isShowingAddProvider = true
                }
            } header: {
                Text("Providers")
            } footer: {
                Text("Works with any service that supports the OpenAI Chat Completions API. API keys are stored in the Keychain.")
            }
        }
        .tint(.brandGreen)
        // 和 Profile 一样露出 appBackground。
        // Shows appBackground, like Profile.
        .scrollContentBackground(.hidden)
        .background(Color.appBackground)
        .navigationTitle("AI Model")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(item: $openedProviderID) { providerID in
            LLMProviderView(providerID: providerID)
        }
        .sheet(isPresented: $isShowingAddProvider) {
            LLMProviderFormSheet(addedProviderID: $openedProviderID)
        }
    }

    // MARK: - 行 / Rows

    /// 一行可选的模型，选中的带勾。
    /// Returns one selectable model row, with a checkmark when it's chosen.
    ///
    /// - Parameters:
    ///   - name: 显示的模型名 / The model name to show.
    ///   - model: 这一行的模型，`nil` 是内置模型 / The row's model; `nil` is the built-in one.
    /// - Returns: 模型行 / The model row.
    private func modelRow(name: String, model: LLMModel?) -> some View {
        let chosen = isChosen(model)

        return Button {
            choose(model)
        } label: {
            HStack(spacing: 12) {
                // 按钮里的 `.primary` 是 tint 色的第一层（会变绿），要用固定的系统文字颜色 `Color.primary`。
                // Inside a button `.primary` is the first level of the tint (green), so the fixed system label color is needed.
                Text(name)
                    .foregroundStyle(Color.primary)

                Spacer(minLength: 12)

                if chosen {
                    Image(systemName: "checkmark")
                        .foregroundStyle(.tint)
                }
            }
        }
        // 让 VoiceOver 读出「已选中」。
        // Lets VoiceOver announce "Selected".
        .accessibilityAddTraits(chosen ? .isSelected : [])
    }

    /// 一行服务商：名字、模型数量、箭头，点了推入服务商页。
    /// Returns one provider row with its name, model count and chevron; tapping it pushes the provider page.
    ///
    /// 按钮会把标签染成 tint 色，`.primary` / `.secondary` / `.tertiary` 在按钮里也会变绿，所以各段用固定的系统颜色。
    /// A button tints its label, and `.primary` / `.secondary` / `.tertiary` turn green inside it too, so each part
    /// uses a fixed system color.
    ///
    /// - Parameter provider: 这一行的服务商 / The row's provider.
    /// - Returns: 服务商行 / The provider row.
    private func providerRow(_ provider: LLMProvider) -> some View {
        Button {
            openedProviderID = provider.id
        } label: {
            HStack(spacing: 12) {
                Text(provider.name)
                    .foregroundStyle(Color.primary)

                Spacer(minLength: 12)

                Text(modelCountText(for: provider))
                    .foregroundStyle(Color.secondary)

                // SwiftUI 没有固定的 `Color.tertiary`，用系统的第三级文字颜色。
                // SwiftUI has no fixed tertiary color, so this uses the system's tertiary label color.
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color(.tertiaryLabel))
            }
        }
    }

    // MARK: - 选择 / Choosing

    /// 这个模型是不是当前选中的。
    /// Returns whether this model is the chosen one.
    ///
    /// - Parameter model: 要判断的模型，`nil` 是内置 / The model to check; `nil` is the built-in one.
    /// - Returns: 选中的是它时为 `true` / `true` when it's the chosen model.
    private func isChosen(_ model: LLMModel?) -> Bool {
        guard let currentSettings = settings.first else { return false }

        return currentSettings.llmModel == model
    }

    /// 选中一个模型。
    /// Chooses a model.
    ///
    /// - Parameter model: 要选的模型，`nil` 是内置 / The model to choose; `nil` is the built-in one.
    private func choose(_ model: LLMModel?) {
        guard let currentSettings = settings.first else { return }

        currentSettings.llmModel = model
    }

    // MARK: - 派生数据 / Derived data

    /// 某个服务商的模型，保持查询的添加顺序。
    /// Returns one provider's models, keeping the query's added order.
    ///
    /// - Parameter provider: 服务商 / The provider.
    /// - Returns: 这个服务商的模型 / The provider's models.
    private func models(of provider: LLMProvider) -> [LLMModel] {
        let providerModels = models.filter { model in
            model.provider.id == provider.id
        }
        return providerModels
    }

    /// 服务商行右侧的模型数量文字。
    /// Returns the model count text on a provider row.
    ///
    /// - Parameter provider: 服务商 / The provider.
    /// - Returns: 「No Models」「1 Model」或「N Models」/ "No Models", "1 Model" or "N Models".
    private func modelCountText(for provider: LLMProvider) -> String {
        let count = models(of: provider).count

        switch count {
        case 0:
            return "No Models"
        case 1:
            return "1 Model"
        default:
            return "\(count) Models"
        }
    }
}

#Preview(traits: .llmProviderSampleData) {
    // 推入的页面，放进 `NavigationStack` 才有导航栏。
    // A pushed page needs a `NavigationStack` for its navigation bar.
    NavigationStack {
        AIModelSettingsView()
    }
}
