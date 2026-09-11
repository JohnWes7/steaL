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
    var body: some Scene {
        WindowGroup {
            RootView()
        }
        // 创建并注入 SwiftData 容器 /
        // Creates and injects the SwiftData container.
        .modelContainer(for: [UserSettingModel.self])
    }
}
