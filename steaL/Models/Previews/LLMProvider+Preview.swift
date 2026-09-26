//
//  LLMProvider+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import SwiftData
import SwiftUI

// 用 `PreviewModifier` 共享一份内存数据，几个页面的预览都能用（WWDC24「What's new in SwiftUI」的写法）/
// A `PreviewModifier` shares one in-memory container across several pages' previews (the WWDC24 "What's new in SwiftUI" pattern)
/// AI 模型设置预览用的样例：设置行、有两个模型且选中其一的服务商、还没有模型的服务商 /
/// Sample data for the AI model settings previews: the settings row, a provider with two models (one chosen), and one with none
struct LLMProviderSampleData: PreviewModifier {

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

    func body(content: Content, context: ModelContainer) -> some View {
        content.modelContainer(context)
    }
}

extension PreviewTrait where T == Preview.ViewTraits {

    /// 带 AI 服务商样例数据的预览 / A preview with the sample AI provider data
    @MainActor static var llmProviderSampleData: Self {
        .modifier(LLMProviderSampleData())
    }
}
