//
//  MediaGalleryView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 详情页顶部的媒体分页：视频封面在前，随后是商店主图与截图；没有媒体时退回 16:9 占位 /
/// The media pager at the top of the detail page: video posters first, then the store header and
/// screenshots, falling back to a 16:9 placeholder
struct MediaGalleryView: View {

    /// 按顺序展示的图片地址，第一张是主图 / The image URLs to show, in order; the first one is the header
    let imageURLs: [String]

    /// 视频，这里只取封面 / Movies; only their posters are used here
    let movies: [SteamMovie]

    /// 一页媒体：视频封面或一张图 / One media page: either a movie poster or an image
    private enum GalleryPage {
        case movie(SteamMovie)
        case image(String)
    }

    /// 摊平成页序：视频封面在前，主图与截图在后 / Flattened page order: posters first, then the header and screenshots
    private var pages: [GalleryPage] {
        movies.map(GalleryPage.movie) + imageURLs.map(GalleryPage.image)
    }

    var body: some View {
        if pages.isEmpty {
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
                ForEach(Array(pages.enumerated()), id: \.offset) { _, page in
                    switch page {
                    case .movie(let movie):
                        mediaPage(movie.posterURL)
                            .overlay { playBadge }
                    case .image(let urlString):
                        mediaPage(URL(string: urlString))
                    }
                }
            }
            .tabViewStyle(.page(indexDisplayMode: pages.count > 1 ? .automatic : .never))
            .frame(maxWidth: .infinity)
            .aspectRatio(16 / 9, contentMode: .fit)
        }
    }

    /// 一页媒体；没地址直接按失败算，否则 `AsyncImage` 会一直转圈 /
    /// One media page; a missing URL counts as a failure instead of spinning forever
    @ViewBuilder
    private func mediaPage(_ url: URL?) -> some View {
        if let url {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                case .failure:
                    failedPage
                case .empty:
                    Color.surfaceElevated
                        .overlay {
                            ProgressView()
                                .tint(.brandGreen)
                        }
                @unknown default:
                    failedPage
                }
            }
            .clipped()
        } else {
            failedPage
        }
    }

    /// 地址取不到或图片加载失败时的占位 / The placeholder for a missing URL or a failed load
    private var failedPage: some View {
        Color.surfaceElevated
            .overlay {
                Image(systemName: "photo.badge.exclamationmark")
                    .font(.system(size: 30, weight: .medium))
                    .foregroundStyle(.secondaryText)
            }
    }

    /// 视频页上的播放按钮，现在只长在封面上 / The play button on movie pages; it only sits on the poster for now
    private var playBadge: some View {
        Button {
            // 接播放器的逻辑下一步再做 / Wiring up the player comes next
        } label: {
            Circle()
                .fill(Color.appBackground.opacity(0.6))
                .frame(width: 60, height: 60)
                .overlay {
                    Image(systemName: "play.fill")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.primaryText)
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Play trailer")
    }
}

#Preview("有图 / With images") {
    MediaGalleryView(
        imageURLs: ["https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg"],
        movies: []
    )
    .background(Color.appBackground)
}

#Preview("视频封面在前 / Movies first") {
    MediaGalleryView(
        imageURLs: ["https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg"],
        movies: [.previewSample]
    )
    .background(Color.appBackground)
}

#Preview("无图 / No images") {
    MediaGalleryView(imageURLs: [], movies: [])
        .background(Color.appBackground)
}
