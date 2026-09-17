//
//  MainTabView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import SwiftUI

struct MainTabView: View {
    /// 选中项。不叫 `Tab` 是为了避开 `SwiftUI.Tab` / Not named `Tab` to avoid colliding with `SwiftUI.Tab`
    enum AppTab {
        case games
        case search
        case wishlist
        case profile
        case test
    }

    @State private var selectedTab: AppTab = .games

    var body: some View {
        TabView(selection: $selectedTab) {
            Tab("Games", systemImage: "gamecontroller.fill", value: AppTab.games) {
                NavigationStack {
                    GamesView()
                }
            }

            // `role: .search` 让系统把搜索框变形顶替 tab bar / `role: .search` morphs a search field into the tab bar
            Tab("Search", systemImage: "magnifyingglass", value: AppTab.search, role: .search) {
                NavigationStack {
                     IOS26SearchView()
                }
            }

            // old version
            // Tab("Search", systemImage: "magnifyingglass", value: AppTab.search) {
            //     NavigationStack {
            //         SearchView(isSelected: true)
            //     }
            // }

            Tab("Wishlist", systemImage: "heart", value: AppTab.wishlist) {
                NavigationStack {
                }
            }

            Tab("Profile", systemImage: "person.fill", value: AppTab.profile) {
                NavigationStack {
                    ProfileView()
                }
            }
            
        }
        .tint(selectedTab == .games ? .brandGreen : .brandBlue)
        // 不加这行，选中搜索 tab 只会变形、不弹键盘 / Without this the search tab only morphs and never raises the keyboard
        .tabViewSearchActivation(.searchTabSelection)
    }
}

#Preview {
    MainTabView()
}
