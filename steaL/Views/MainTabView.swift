//
//  MainTabView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import SwiftUI

struct MainTabView: View {
    var body: some View {
        TabView {
            NavigationStack {
                GameHomeView()
            }
            .tabItem { Label("Games", systemImage: "gamecontroller.fill") }
            
            NavigationStack {

            }
            .tabItem { Label("Search", systemImage: "magnifyingglass") }
            
            NavigationStack {
                
            }
            .tabItem { Label("Wishlist", systemImage: "heart") }

            NavigationStack {

            }
            .tabItem { Label("Profile", systemImage: "person.fill") }
        }
        .tint(.brandBlue)
    }
}

private struct GameHomeView: View {
    private let categories = ["All", "Action", "RPG", "Strategy", "Adventure"]
    @State private var selectedCategory = "All"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                header
                categoryPicker
                hotDeals
                flashSale
                trendingHeader
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
        }
        .scrollIndicators(.hidden)
        .background(Color.appBackground)
        .navigationBarHidden(true)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("STEAM PRICE TRACKER")
                        .font(.caption.weight(.bold))
                        .tracking(2)
                        .foregroundStyle(.brandGreen)

                    Text("stea")
                        .font(.system(size: 42, weight: .heavy, design: .rounded))
                        .foregroundStyle(Color.primaryText)
                    + Text("L")
                        .font(.system(size: 42, weight: .heavy, design: .rounded))
                        .foregroundStyle(.brandBlue)
                }

                Spacer()

                Button(action: {}) {
                    Image(systemName: "bell.fill")
                        .font(.title3)
                        .foregroundStyle(Color.primaryText)
                        .frame(width: 48, height: 48)
                        .background(Color.surfaceElevated, in: Circle())
                        .overlay(alignment: .topTrailing) {
                            Circle()
                                .fill(Color.brandBlue)
                                .frame(width: 11, height: 11)
                        }
                }
                .accessibilityLabel("Notifications")
            }

            HStack(spacing: 12) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(Color.secondaryText)
                Text("Search games, publishers...")
                    .foregroundStyle(Color.secondaryText)
                Spacer()
            }
            .font(.body)
            .padding(.horizontal, 18)
            .frame(height: 62)
            .background(Color.surfaceElevated, in: RoundedRectangle(cornerRadius: 16))
        }
    }

    private var categoryPicker: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 12) {
                ForEach(categories, id: \.self) { category in
                    Button(category) {
                        selectedCategory = category
                    }
                    .font(.body.weight(.semibold))
                    .foregroundStyle(selectedCategory == category ? Color.appBackground : Color.secondaryText)
                    .padding(.horizontal, 25)
                    .frame(height: 48)
                    .background(selectedCategory == category ? Color.brandGreen : Color.surfaceElevated, in: Capsule())
                }
            }
        }
        .scrollIndicators(.hidden)
    }

    private var hotDeals: some View {
        VStack(alignment: .leading, spacing: 16) {
            Label("Hot Deals", systemImage: "flame.fill")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.primaryText)
                .symbolRenderingMode(.multicolor)

            DealCard()
        }
    }

    private var flashSale: some View {
        HStack(spacing: 18) {
            VStack(alignment: .leading, spacing: 8) {
                Text("LIMITED TIME")
                    .font(.caption.weight(.bold))
                    .tracking(1.5)
                    .foregroundStyle(Color.brandBlue)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Color.brandBlue.opacity(0.15), in: Capsule())

                Text("Flash Sale")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Color.primaryText)
                Text("Up to ") + Text("-85%").foregroundStyle(Color.brandGreen).bold() + Text(" off")
            }
            .foregroundStyle(Color.secondaryText)

            Spacer(minLength: 0)

            HStack(spacing: 8) {
                CountdownUnit(value: "08", label: "hr")
                CountdownUnit(value: "24", label: "min")
                CountdownUnit(value: "13", label: "sec")
            }
        }
        .padding(20)
        .background(Color.saleSurface, in: RoundedRectangle(cornerRadius: 24))
        .overlay(alignment: .top) {
            Rectangle().fill(Color.brandGreen).frame(height: 3)
        }
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }

    private var trendingHeader: some View {
        HStack {
            Label("Trending Drops", systemImage: "arrow.down.right")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.primaryText)
            Spacer()
            Button("See All", action: {})
                .font(.body.weight(.semibold))
                .foregroundStyle(Color.brandBlue)
        }
    }
}

private struct DealCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                AsyncImage(url: URL(string: "https://cdn.akamai.steamstatic.com/steam/apps/1091500/header.jpg")) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.saleSurface
                }
                .frame(height: 235)
                .clipped()
                .overlay(alignment: .bottom) {
                    LinearGradient(colors: [.clear, Color.appBackground.opacity(0.9)], startPoint: .top, endPoint: .bottom)
                }

                Text("-60%")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color.brandBlue, in: Capsule())
                    .padding(18)
            }
            .overlay(alignment: .bottomLeading) {
                VStack(alignment: .leading, spacing: 7) {
                    Text("RPG")
                        .font(.caption.weight(.heavy))
                        .tracking(2)
                        .foregroundStyle(Color.brandGreen)
                    Text("Cyberpunk 2077")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(Color.primaryText)
                    Label("4.5 (124K)", systemImage: "star.fill")
                        .font(.subheadline)
                        .foregroundStyle(Color.secondaryText)
                        .symbolRenderingMode(.multicolor)
                }
                .padding(20)
            }

            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("$23.99")
                        .font(.title.weight(.bold))
                        .foregroundStyle(Color.brandGreen)
                    Text("$59.99")
                        .font(.subheadline)
                        .strikethrough()
                        .foregroundStyle(Color.secondaryText)
                }
                Spacer()
                Text("Steam")
                Text("GOG")
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(Color.secondaryText)
            .padding(20)
            .background(Color.surfaceElevated)
        }
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }
}

private struct CountdownUnit: View {
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.title2.monospacedDigit().weight(.bold))
                .foregroundStyle(Color.primaryText)
            Text(label)
                .font(.caption2)
                .foregroundStyle(Color.secondaryText)
        }
        .frame(width: 54, height: 74)
        .background(Color.countdownSurface, in: RoundedRectangle(cornerRadius: 12))
    }
}

#Preview {
    MainTabView()
}
