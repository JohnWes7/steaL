//
//  GamesView.swift
//  steaL
//
//  Created by yijiang on 2026/9/8.
//

import SwiftUI

struct GamesView: View {
    @State private var searchText = ""
    
    var body: some View {
        ZStack(alignment: .top) {
            Color.appBackground
                .ignoresSafeArea()
            
            ScrollView {
                LazyVStack(spacing: 12) {
                    Color.clear
                        .frame(height: 140)
                    
                    // 游戏列表放在这里
                    ForEach(0..<20, id: \.self) { index in
                        Text("Game \(index + 1)")
                            .foregroundStyle(.primaryText)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                            .background(Color.surfaceElevated)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 16)
            }
            
            headerArea
        }
        .toolbar(.hidden, for: .navigationBar)
    }
    
    private var headerArea: some View {
        VStack(alignment: .leading, spacing: 8) {
            header
            searchField
        }
        .padding(.horizontal, 14)
        .padding(.top, 2)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.bar)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(.white.opacity(0.08))
                .frame(height: 0.5)
        }
    }
    
    @ViewBuilder
    private var header: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 0) {
                Text("STEAM PRICE TRACKER")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.brandGreen)
                
                HStack(spacing: 0) {
                    Text("stea")
                        .foregroundStyle(.primaryText)
                    
                    Text("L")
                        .foregroundStyle(.brandBlue)
                }
                .font(.system(size: 44, weight: .heavy, design: .rounded))
            }
            
            Spacer()
            
            notificationButton
        }
    }
    
    @ViewBuilder
    private var notificationButton: some View {
        Button {
            // 打开通知页面
        } label: {
            Image(systemName: "bell.fill")
                .font(.system(size: 21, weight: .semibold))
                .foregroundStyle(.primaryText)
                .frame(width: 56, height: 56)
                .background(Color.surfaceElevated)
                .clipShape(Circle())
                .overlay(alignment: .topTrailing) {
                    Circle()
                        .fill(Color.brandBlue)
                        .frame(width: 11, height: 11)
                        .offset(x: -8, y: 8)
                }
        }
        .accessibilityLabel("Notifications")
    }
    
    @ViewBuilder
    private var searchField: some View {
        HStack(spacing: 14) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 19, weight: .medium))
                .foregroundStyle(.secondaryText)
            
            TextField("Search games, publishers...", text: $searchText)
                .foregroundStyle(.primaryText)
                .tint(.brandBlue)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
        }
        .padding(.horizontal, 20)
        .frame(height: 45)
        .background(Color.appBackground)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

#Preview {
    NavigationStack {
        GamesView()
    }
}
