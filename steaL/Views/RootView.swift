//
//  RootView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import SwiftUI
import SwiftData

struct RootView: View {

    /// 用户设置单例行，这里只读显示主题 / Singleton user-settings row; only the display theme is read here
    @Query(filter: #Predicate<UserSettingModel> { $0.id == "main" })
    private var settings: [UserSettingModel]

    var body: some View {
        // 之后加入登陆界面 判断需要登陆了之后才进入主界面
        MainTabView()
            // 加在根上会作用于整个窗口（所有 tab 与 sheet）；`nil` 交还给系统 /
            // Applied at the root so it covers the whole window (every tab and sheet); `nil` hands control back to the system
            .preferredColorScheme(settings.first?.theme.colorScheme)
    }
}


// Option + Command + Return
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
