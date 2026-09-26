//
//  AIModelSettingsView.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import SwiftData
import SwiftUI

// 这一页只负责「选」：服务商的编辑、加模型、删除在 `LLMProviderView`，同设置 → 无线局域网（点行连接、点 ⓘ 进详情）/
// This page only picks; editing a provider, adding models and deleting live in `LLMProviderView`,
// like Settings → Wi-Fi (tap a row to join, tap ⓘ for details)
/// 从 Profile 的 AI Model 行推入：内置模型与各服务商的模型按组列出，整页一个勾；最后是服务商列表 /
/// Pushed from Profile's AI Model row: the built-in model and every provider's models in groups sharing one checkmark,
/// followed by the list of providers
struct AIModelSettingsView: View {

    /// 用户设置单例行，选中的模型存在它的 `llmModel` / The singleton settings row; the chosen model lives in its `llmModel`
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    /// 用户添加的服务商，按添加顺序 / The user's providers, in the order they were added
    @Query(sort: \LLMProvider.createdAt)
    private var providers: [LLMProvider]

    /// 是否弹出 Add Provider 表单 / Whether the Add Provider form is shown
    @State private var isShowingAddProvider = false

    /// 表单刚添加的服务商；有值就推入它的页面，好接着加模型 / The provider the form just added; pushes its page so models can be added next
    @State private var addedProvider: LLMProvider?

    var body: some View {
        List {
            Section("Built-in") {
                modelRow(name: Config.llmModel, model: nil)
            }

            // 没有模型的服务商没有可选的，不单独占一组 / A provider without models has nothing to pick, so it gets no group here
            ForEach(providersWithModels) { provider in
                Section(provider.name) {
                    ForEach(provider.sortedModels) { model in
                        modelRow(name: model.name, model: model)
                    }
                }
            }

            Section {
                ForEach(providers) { provider in
                    NavigationLink {
                        LLMProviderView(provider: provider)
                    } label: {
                        LabeledContent(provider.name, value: modelCountText(for: provider))
                    }
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
        // 和 Profile 一样露出 appBackground / Shows appBackground, like Profile
        .scrollContentBackground(.hidden)
        .background(Color.appBackground)
        .navigationTitle("AI Model")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(item: $addedProvider) { provider in
            LLMProviderView(provider: provider)
        }
        .sheet(isPresented: $isShowingAddProvider) {
            LLMProviderFormSheet(addedProvider: $addedProvider)
        }
    }

    // MARK: - 行 / Rows

    /// 一行可选的模型，选中的带勾；`model` 为 `nil` 是内置模型 /
    /// One selectable model row with a checkmark when chosen; a `nil` model is the built-in one
    private func modelRow(name: String, model: LLMModel?) -> some View {
        let chosen = isChosen(model)

        return Button {
            choose(model)
        } label: {
            HStack(spacing: 12) {
                // 按钮默认把文字染成 tint 色，选项行要保持正文颜色 / Buttons tint their text; an option row keeps the body color
                Text(name)
                    .foregroundStyle(.primary)

                Spacer(minLength: 12)

                if chosen {
                    Image(systemName: "checkmark")
                        .foregroundStyle(.tint)
                }
            }
        }
        // 让 VoiceOver 读出「已选中」/ Lets VoiceOver announce "Selected"
        .accessibilityAddTraits(chosen ? .isSelected : [])
    }

    // MARK: - 选择 / Choosing

    /// 这个模型是不是当前选中的；`nil` 是内置 / Whether this model is the chosen one; `nil` is the built-in one
    private func isChosen(_ model: LLMModel?) -> Bool {
        guard let currentSettings = settings.first else { return false }

        return currentSettings.llmModel == model
    }

    /// 选中一个模型；`nil` 是内置 / Chooses a model; `nil` is the built-in one
    private func choose(_ model: LLMModel?) {
        guard let currentSettings = settings.first else { return }

        currentSettings.llmModel = model
    }

    // MARK: - 派生数据 / Derived data

    /// 至少有一个模型的服务商 / The providers that have at least one model
    private var providersWithModels: [LLMProvider] {
        let withModels = providers.filter { provider in
            !provider.models.isEmpty
        }
        return withModels
    }

    /// 服务商行右侧的模型数量 / The model count on a provider row
    private func modelCountText(for provider: LLMProvider) -> String {
        let count = provider.models.count

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
    // 推入的页面，放进 `NavigationStack` 才有导航栏 / A pushed page; it needs a `NavigationStack` for its navigation bar
    NavigationStack {
        AIModelSettingsView()
    }
}
