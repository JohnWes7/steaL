//
//  MainTabView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import SwiftUI

struct MainTabView: View {
    enum Tab {
        case games
        case search
        case wishlist
        case profile
    }

    @State private var selectedTab: Tab = .games

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                GamesView()
            }
            .tabItem { Label("Games", systemImage: "gamecontroller.fill") }
            .tag(Tab.games)

            NavigationStack {
            }
            .tabItem { Label("Search", systemImage: "magnifyingglass") }
            .tag(Tab.search)

            NavigationStack {
            }
            .tabItem { Label("Wishlist", systemImage: "heart") }
            .tag(Tab.wishlist)

            NavigationStack {
            }
            .tabItem { Label("Profile", systemImage: "person.fill") }
            .tag(Tab.profile)
        }
        .tint(selectedTab == .games ? .brandGreen : .brandBlue)
    }
}

#Preview {
    MainTabView()
}
