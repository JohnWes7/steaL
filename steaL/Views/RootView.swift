//
//  RootView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import SwiftUI
import SwiftData

struct RootView: View {
    var body: some View {
        // 之后加入登陆界面 判断需要登陆了之后才进入主界面
        MainTabView()
    }
}


#Preview {
    let container = try! ModelContainer(
        for: UserSettingModel.self,
        configurations: ModelConfiguration(isStoredInMemoryOnly: true)
    )
    let _ = container.mainContext.insert(UserSettingModel())
    
    // 复刻 `MainTabView`：`toolbar` 只在 `NavigationStack` 里生效 / Mirrors `MainTabView`; the modifier needs a `NavigationStack`
    RootView()
        .modelContainer(container)
}

