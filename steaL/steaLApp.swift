//
//  steaLApp.swift
//  steaL
//
//  Created by yijiang on 2026/8/28.
//

import SwiftData
import SwiftUI

@main
struct steaLApp: App {
    /// 全 App 共用的 SwiftData 容器 / The SwiftData container shared by the whole app
    private let container: ModelContainer

    init() {
        let container: ModelContainer
        do {
            container = try ModelContainer(for: UserSettingModel.self)
        } catch {
            fatalError("Failed to create ModelContainer / 创建容器失败: \(error)")
        }
        self.container = container

        // 首帧渲染前保证设置行存在 / Makes sure the settings row exists before the first frame
        let settings = bootstrapSettings(in: container.mainContext)

        #if DEBUG
        // 启动时打印当前设置内容，方便调试 / Logs the current settings at launch, for debugging
        print("""
        [Settings] 当前设置 / current settings:
          id: \(settings.id)
          theme: \(settings.theme.rawValue)
          region: \(settings.region.rawValue)
          language: \(settings.language.rawValue)
          priceDropAlerts: \(settings.isPriceDropAlertsEnabled)
          gameReleaseAlerts: \(settings.isGameReleaseAlertsEnabled)
        """)
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .modelContainer(container)
    }

    /// 保证单例设置行存在并返回：已有则沿用，没有则插入一条默认设置 /
    /// Makes sure the singleton settings row exists and returns it: keeps the stored one, or inserts the defaults
    private func bootstrapSettings(in context: ModelContext) -> UserSettingModel {
        let descriptor = FetchDescriptor<UserSettingModel>(
            predicate: #Predicate { $0.id == "main" }
        )

        if let existing = try? context.fetch(descriptor).first {
            return existing
        }

        let settings = UserSettingModel()
        context.insert(settings)
        return settings
    }
}
