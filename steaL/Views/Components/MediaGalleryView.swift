//
//  MediaGalleryView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 详情页顶部的图片分页：第一张是 Steam 商店主图，随后是 Steam 截图；
/// 没有图片时退回一块 16:9 的占位面 /
/// The media pager at the top of the detail page: Steam's store header first, then the screenshots;
/// with no images it falls back to a 16:9 placeholder
struct MediaGalleryView: View {

    /// 按顺序展示的图片地址，第一张是主图 / The image URLs to show, in order; the first one is the header
    let imageURLs: [String]

    var body: some View {
        if imageURLs.isEmpty {
            Color.surfaceElevated
                .frame(maxWidth: .infinity)
                .aspectRatio(16 / 9, contentMode: .fit)
                .overlay {
                    Image(systemName: "photo")
                        .font(.system(size: 30, weight: .medium))
                        .foregroundStyle(.secondaryText)
                }
        } else {
            TabView {
                ForEach(Array(imageURLs.enumerated()), id: \.offset) { _, imageURL in
                    AsyncImage(url: URL(string: imageURL)) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .scaledToFill()
                        case .failure:
                            Color.surfaceElevated
                                .overlay {
                                    Image(systemName: "photo.badge.exclamationmark")
                                        .font(.system(size: 30, weight: .medium))
                                        .foregroundStyle(.secondaryText)
                                }
                        case .empty:
                            Color.surfaceElevated
                                .overlay {
                                    ProgressView()
                                        .tint(.brandGreen)
                                }
                        @unknown default:
                            Color.surfaceElevated
                        }
                    }
                    .clipped()
                }
            }
            .tabViewStyle(.page(indexDisplayMode: imageURLs.count > 1 ? .automatic : .never))
            .frame(maxWidth: .infinity)
            .aspectRatio(16 / 9, contentMode: .fit)
        }
    }
}

#Preview("有图 / With images") {
    MediaGalleryView(
        imageURLs: ["https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg"]
    )
    .background(Color.appBackground)
}

#Preview("无图 / No images") {
    MediaGalleryView(imageURLs: [])
        .background(Color.appBackground)
}
