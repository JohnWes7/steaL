//
//  MainTabView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import SwiftUI

struct MainTabView: View {
    /// 选中项。原来叫 `Tab`，改用 iOS 26 的 `Tab` API 后会和 `SwiftUI.Tab` 撞名，
    /// 所以改名为 `AppTab` /
    /// The selection type. It used to be named `Tab`, which collides with `SwiftUI.Tab` once we
    /// adopt the iOS 26 `Tab` API, so it is now `AppTab`.
    enum AppTab {
        case games
        case search
        case wishlist
        case profile
    }

    @State private var selectedTab: AppTab = .games

    var body: some View {
        TabView(selection: $selectedTab) {
            Tab("Games", systemImage: "gamecontroller.fill", value: AppTab.games) {
                NavigationStack {
                    GamesView {
                        // 顶部搜索框暂时保留：只负责切到搜索 tab。
                        // 搜索框的聚焦、弹键盘交给系统（搜索 tab 声明了 role: .search）/
                        // The top search bar is still here for now and only switches tabs.
                        // Focusing and raising the keyboard are the system's job now that the
                        // search tab declares `role: .search`
                        selectedTab = .search
                    }
                }
            }

            // `role: .search` 是整套原生效果的唯一开关：系统会把其他 tab 向左收拢，
            // 让搜索框变形顶替 tab bar，并自动聚焦键盘 /
            // `role: .search` is the single switch for the whole native effect: the system collapses
            // the other tabs to the left, morphs a search field into place of the tab bar and
            // focuses the keyboard for us
            Tab("Search", systemImage: "magnifyingglass", value: AppTab.search, role: .search) {
                NavigationStack {
                    IOS26SearchView()
                }
            }

            Tab("Wishlist", systemImage: "heart", value: AppTab.wishlist) {
                NavigationStack {
                }
            }

            Tab("Profile", systemImage: "person.fill", value: AppTab.profile) {
                NavigationStack {
                }
            }
        }
        .tint(selectedTab == .games ? .brandGreen : .brandBlue)
    }
}

#Preview {
    MainTabView()
}
