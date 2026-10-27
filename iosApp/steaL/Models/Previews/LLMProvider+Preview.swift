//
//  LLMProvider+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import SwiftData
import SwiftUI

/// AI 模型设置预览用的样例数据。
/// Sample data for the AI model settings previews.
///
/// 包含设置行、一个有两个模型且选中其一的服务商、一个还没有模型的服务商。
/// 用 `PreviewModifier` 共享一份内存数据，几个页面的预览都能用。
/// Holds the settings row, a provider with two models (one chosen), and a provider with none.
/// A `PreviewModifier` shares one in-memory container across several pages' previews.
struct LLMProviderSampleData: PreviewModifier {

    /// 建好内存容器并插入样例数据。
    /// Builds the in-memory container and inserts the sample data.
    ///
    /// - Returns: 带样例数据的容器 / A container holding the sample data.
    /// - Throws: 容器建不起来时抛出的错误 / The error thrown when the container can't be created.
    static func makeSharedContext() async throws -> ModelContainer {
        let configuration = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try ModelContainer(
            for: UserSettingModel.self, LLMProvider.self, LLMModel.self,
            configurations: configuration
        )
        let context = container.mainContext

        let settings = UserSettingModel()
        context.insert(settings)

        let openRouter = LLMProvider(name: "My OpenRouter", baseURL: "https://openrouter.ai/api/v1")
        context.insert(openRouter)

        let gptModel = LLMModel(name: "openai/gpt-5-mini", provider: openRouter)
        context.insert(gptModel)

        let claudeModel = LLMModel(name: "anthropic/claude-sonnet-4.5", provider: openRouter)
        context.insert(claudeModel)

        let deepSeek = LLMProvider(name: "My DeepSeek", baseURL: "https://api.deepseek.com")
        context.insert(deepSeek)

        settings.llmModel = claudeModel

        return container
    }

    /// 把样例容器装到预览的视图上。
    /// Attaches the sample container to the previewed view.
    ///
    /// - Parameters:
    ///   - content: 被预览的视图 / The view being previewed.
    ///   - context: `makeSharedContext()` 建好的容器 / The container from `makeSharedContext()`.
    /// - Returns: 装好容器的视图 / The view with the container attached.
    func body(content: Content, context: ModelContainer) -> some View {
        content.modelContainer(context)
    }
}

extension PreviewTrait where T == Preview.ViewTraits {

    /// 带 AI 服务商样例数据的预览。
    /// A preview with the sample AI provider data.
    @MainActor static var llmProviderSampleData: Self {
        .modifier(LLMProviderSampleData())
    }
}
