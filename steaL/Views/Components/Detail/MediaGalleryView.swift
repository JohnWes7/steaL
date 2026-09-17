//
//  MediaGalleryView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import AVKit
import SwiftUI

/// 详情页顶部的媒体分页：视频封面在前，随后是商店主图与截图；没有媒体时退回 16:9 占位 /
/// The media pager at the top of the detail page: video posters first, then the store header and
/// screenshots, falling back to a 16:9 placeholder
struct MediaGalleryView: View {

    /// 按顺序展示的图片地址，第一张是主图 / The image URLs to show, in order; the first one is the header
    let imageURLs: [String]

    /// 视频，分页显示封面，点击后原位播放 / Movies shown as posters in the pager and played in place when tapped
    let movies: [SteamMovie]

    /// 全页共用一个播放器 / One player shared by the whole gallery
    @State private var player = AVPlayer()
    /// 正在播的那条，`nil` = 所有页都停在封面 / The movie being played; `nil` means every page shows its poster
    @State private var playingMovie: SteamMovie?
    /// 当前页，划走时要靠它停播 / The current page, needed to stop playback when it is swiped away
    @State private var page = 0

    /// 一页媒体：视频封面或一张图 / One media page: either a movie poster or an image
    private enum GalleryPage {
        case movie(SteamMovie)
        case image(String)
    }

    /// 摊平成页序：视频封面在前，主图与截图在后 / Flattened page order: posters first, then the header and screenshots
    private var pages: [GalleryPage] {
        movies.filter(\.isPlayable).map(GalleryPage.movie) + imageURLs.map(GalleryPage.image)
    }

    var body: some View {
        Group {
            if pages.isEmpty {
                Color.surfaceElevated
                    .frame(maxWidth: .infinity)
                    .aspectRatio(16 / 9, contentMode: .fit)
                    .overlay {
                        Image(systemName: "photo")
                            .font(.system(size: 30, weight: .medium))
                            .foregroundStyle(.secondaryText)
                    }
            } else if playingMovie != nil {
                VideoPlayer(player: player)
                    .frame(maxWidth: .infinity)
                    .aspectRatio(16 / 9, contentMode: .fit)
                    .overlay(alignment: .topLeading) {
                        Button(action: stop) {
                            Image(systemName: "xmark")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(.primaryText)
                                .frame(width: 36, height: 36)
                                .background(Color.appBackground.opacity(0.6), in: Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Close trailer")
                        .padding(12)
                    }
            } else {
                TabView(selection: $page) {
                    ForEach(Array(pages.enumerated()), id: \.offset) { index, item in
                        Group {
                            switch item {
                            case .movie(let movie):
                                moviePage(movie)
                            case .image(let urlString):
                                mediaPage(URL(string: urlString))
                            }
                        }
                        .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: pages.count > 1 ? .automatic : .never))
                .frame(maxWidth: .infinity)
                .aspectRatio(16 / 9, contentMode: .fit)
                .onChange(of: page) { stop() }
            }
        }
        .onDisappear { stop() }
    }

    /// 一页媒体；没地址直接按失败算，否则 `AsyncImage` 会一直转圈 /
    /// One media page; a missing URL counts as a failure instead of spinning forever
    @ViewBuilder
    private func mediaPage(_ url: URL?) -> some View {
        if let url {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    // 完整图驱动布局：`scaledToFill` 报告的是比框更大的 cover 尺寸，放进 `ZStack` 会把页面撑宽 /
                    // The fitted copy drives layout: `scaledToFill` reports an oversized cover size that would widen a `ZStack`
                    image
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background {
                            ZStack {
                                image
                                    .resizable()
                                    .scaledToFill()
                                    .blur(radius: 20)

                                Color.appBackground.opacity(0.5)
                            }
                        }
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

    /// 视频页：封面加播放按钮 / A movie page: a poster with a play button
    private func moviePage(_ movie: SteamMovie) -> some View {
        mediaPage(movie.thumbnail.flatMap { URL(string: $0) })
            .overlay { playBadge(movie) }
    }

    /// 视频页上的播放按钮 / The play button on a movie page
    private func playBadge(_ movie: SteamMovie) -> some View {
        Button {
            play(movie)
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

    /// 换 item 并起播 / Swaps the item and starts playback
    private func play(_ movie: SteamMovie) {
        guard let urlString = movie.preferredURL, let url = URL(string: urlString) else { return }

        player.replaceCurrentItem(with: AVPlayerItem(url: url))
        playingMovie = movie
        player.play()
    }

    /// 停播并释放当前视频 / Stops playback and releases the current video
    private func stop() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        playingMovie = nil
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
