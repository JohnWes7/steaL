//
//  UserSettingModel+Preview.swift
//  steaL
//
//  Created by yijiang on 2026/9/27.
//

import SwiftData
import SwiftUI

/// 只有一条默认设置行的预览数据。
/// Preview data holding just the default settings row.
///
/// 页面用 `@Query` 取 `main` 行，空容器里 `settings.first` 是 `nil`，页面会空白或走兜底，所以先插一条默认设置。
/// Pages fetch the "main" row with `@Query`; in an empty container `settings.first` is `nil` and the page renders
/// blank or falls back, so a default row is inserted first.
struct UserSettingSampleData: PreviewModifier {

    /// 建好内存容器并插入默认设置行。
    /// Builds the in-memory container and inserts the default settings row.
    ///
    /// - Returns: 带设置行的容器 / A container holding the settings row.
    /// - Throws: 容器建不起来时抛出的错误 / The error thrown when the container can't be created.
    static func makeSharedContext() async throws -> ModelContainer {
        let configuration = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try ModelContainer(
            for: UserSettingModel.self, LLMProvider.self, LLMModel.self,
            configurations: configuration
        )

        let settings = UserSettingModel()
        container.mainContext.insert(settings)

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

    /// 带一条默认设置行的预览。
    /// A preview with one default settings row.
    @MainActor static var userSettingSampleData: Self {
        .modifier(UserSettingSampleData())
    }
}
