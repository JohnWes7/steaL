//
//  MediaGalleryView.swift
//  steaL
//
//  Created by yijiang on 2026/9/15.
//

import SwiftUI

/// 详情页顶部的媒体分页：商店主图在前，随后是视频封面与截图；没有媒体时退回 16:9 占位 /
/// The media pager at the top of the detail page: the store header first, then video posters and
/// screenshots, falling back to a 16:9 placeholder
struct MediaGalleryView: View {

    /// 商店主图，放在第一页 / The store header image, shown on the first page
    let headerImageURL: String?

    /// 视频，分页显示封面，点击后在原页播放 / Movies shown as posters in the pager and played in their own page when tapped
    let movies: [SteamMovie]

    /// 截图，排在视频之后 / Screenshots, shown after the movies
    let screenshotURLs: [String]

    /// 预告片播放状态，全画廊共用一个播放器 / Trailer playback state; one player serves the whole gallery
    @State private var trailer = TrailerPlayer()
    /// 当前页（`scrollPosition` 的 id），`body` 不读它，翻页时只有分页点重绘 /
    /// The current page (the `scrollPosition` id); `body` never reads it, so paging only redraws the dots
    @State private var page: Int? = 0

    /// 一页媒体：视频封面或一张图 / One media page: either a movie poster or an image
    private enum GalleryPage {
        case movie(SteamMovie)
        case image(String)
    }

    /// 摊平成页序：主图 → 视频封面 → 截图 / Flattened page order: header, then posters, then screenshots
    private var pages: [GalleryPage] {
        let header: [GalleryPage] = headerImageURL.map { [.image($0)] } ?? []
        return header
            + movies.filter(\.isPlayable).map(GalleryPage.movie)
            + screenshotURLs.map(GalleryPage.image)
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
            } else {
                pager
            }
        }
        // 弹出全屏也会触发 `onDisappear`，那时不能停播 / Presenting full screen also fires `onDisappear`; keep playing then
        .onDisappear {
            if !trailer.isFullScreen {
                trailer.stop()
            }
        }
        .fullScreenCover(isPresented: $trailer.isFullScreen) {
            TrailerFullScreenView(player: trailer.player, title: trailer.movie?.name ?? "Trailer")
        }
    }

    /// 分页区与下方的分页点；分页点不叠在画面上，所以不会挡视频 /
    /// The pager and the dots below it; the dots sit outside the picture so they never cover a video
    private var pager: some View {
        VStack(spacing: 20) {
            ScrollView(.horizontal) {
                LazyHStack(spacing: 0) {
                    ForEach(Array(pages.enumerated()), id: \.offset) { _, item in
                        Group {
                            switch item {
                            case .movie(let movie):
                                moviePage(movie)
                            case .image(let urlString):
                                mediaPage(URL(string: urlString))
                            }
                        }
                        .containerRelativeFrame([.horizontal, .vertical])
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $page)
            .scrollIndicators(.hidden)
            // 停稳后再判断，滑到一半时不做副作用 / Decide once the pager settles, never halfway through a swipe
            .onScrollPhaseChange { _, newPhase in
                if newPhase == .idle {
                    stopTrailerIfPagedAway()
                }
            }
            .frame(maxWidth: .infinity)
            .aspectRatio(16 / 9, contentMode: .fit)

            if pages.count > 1 {
                GalleryPageControl(numberOfPages: pages.count, currentPage: $page)
            }
        }
    }

    /// 停稳的那页不是正在播的视频就停播；划一点又回原页不会停 /
    /// Stops the trailer when the settled page is not the one playing; a small swipe back keeps it going
    private func stopTrailerIfPagedAway() {
        guard let playing = trailer.movie else { return }

        if let page, pages.indices.contains(page), case .movie(let movie) = pages[page], movie == playing {
            return
        }
        trailer.stop()
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

    /// 视频页：封面加播放按钮，播放中把内嵌播放器盖在上面 /
    /// A movie page: a poster with a play button, covered by the inline player while playing
    private func moviePage(_ movie: SteamMovie) -> some View {
        let isPlaying = trailer.movie == movie

        // 封面和按钮一直留着：玻璃按钮在按下回弹前被移除，之后新建的按钮会带着放大残留（实测） /
        // The poster and button stay put: removing a glass button before its press springs back
        // leaves the next one stuck enlarged (observed)
        return mediaPage(movie.thumbnail.flatMap { URL(string: $0) })
            .overlay {
                playBadge(movie)
                    .accessibilityHidden(isPlaying)
            }
            .overlay {
                if isPlaying {
                    TrailerPlayerView(trailer: trailer)
                }
            }
    }

    /// 视频页上的播放按钮 / The play button on a movie page
    private func playBadge(_ movie: SteamMovie) -> some View {
        Button {
            trailer.play(movie)
        } label: {
            Image(systemName: "play.fill")
                .font(.system(size: 22, weight: .semibold))
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .accessibilityLabel("Play trailer")
    }
}

#Preview("有图 / With images") {
    MediaGalleryView(
        headerImageURL: "https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg",
        movies: [],
        screenshotURLs: []
    )
    .background(Color.appBackground)
}

#Preview("主图、视频、截图 / Header, movies, screenshots") {
    MediaGalleryView(
        headerImageURL: "https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg",
        movies: [.previewSample],
        screenshotURLs: ["https://cdn.cloudflare.steamstatic.com/steam/apps/620/capsule_616x353.jpg"]
    )
    .background(Color.appBackground)
}

#Preview("无图 / No images") {
    MediaGalleryView(headerImageURL: nil, movies: [], screenshotURLs: [])
        .background(Color.appBackground)
}
