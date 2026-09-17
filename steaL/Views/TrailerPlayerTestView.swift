//
//  TrailerPlayerTestView.swift
//  steaL
//
//  Created by yijiang on 2026/9/17.
//

import AVKit
import SwiftUI

/// 临时验证页：拉真实 Steam 数据，把 `movies` 逐条列出来试播 /
/// A scratch page that pulls real Steam data and plays every entry of `movies`
struct TrailerPlayerTestView: View {

    @State private var details: SteamAppDetails?
    @State private var statusText = "Loading…"

    /// 全页共用一个播放器，切条目用 `replaceCurrentItem(with:)` /
    /// One player for the whole page; switching items replaces the current one
    @State private var player = AVPlayer()
    @State private var playingURL: String?

    /// 主清单里的清晰度阶梯 / The quality ladder read from the master playlist
    @State private var qualities: [Quality] = []
    /// `nil` = Auto，交给系统自适应 / `nil` means Auto, i.e. hand control back to ABR
    @State private var selectedQuality: CGSize?

    /// 想换游戏就设 `STEAL_APPID`，默认 ELDEN RING（5 个视频），Portal 2 是 620 /
    /// Set `STEAL_APPID` to switch games; defaults to ELDEN RING, which has 5 movies
    private let appID = Int(ProcessInfo.processInfo.environment["STEAL_APPID"] ?? "") ?? 1245620

    /// 一个清晰度档位，从 HLS 主清单读出来 / One quality step, read from the HLS master playlist
    private struct Quality: Identifiable, Hashable {
        let size: CGSize
        let peakBitRate: Double

        var id: String { "\(Int(size.width))x\(Int(size.height))" }
        /// 用高度当标签，如 `720p` / The height doubles as the label
        var label: String {
            "\(Int(size.height))p · \(String(format: "%.1f", peakBitRate / 1_000_000)) Mbps"
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                playerBox
                movieRows
                Text("dash_av1 / dash_h264 是 DASH 清单，AVPlayer 播不了，所以只用 hls_h264 / DASH manifests are not playable in AVPlayer, so only hls_h264 is used")
                    .font(.system(size: 13, weight: .regular, design: .rounded))
                    .foregroundStyle(.secondaryText)

                rawSection
            }
            .padding(20)
        }
        .background(Color.appBackground)
        .task { await load() }
    }

    /// 最底部：`movies` 的原样数据，键名就是 Steam 的原始 JSON 键名 /
    /// Bottom: `movies` as-is, keyed by Steam's own JSON key names
    private var rawSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("movies raw")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(.primaryText)

            Text(moviesJSON)
                .font(.system(size: 11, weight: .regular, design: .monospaced))
                .foregroundStyle(.secondaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            Color.surfaceElevated,
            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
        )
    }

    /// 把解码后的 `movies` 再编回 JSON / Encodes the decoded `movies` back into JSON
    private var moviesJSON: String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]

        guard let movies = details?.movies,
            let data = try? encoder.encode(movies),
            let text = String(data: data, encoding: .utf8)
        else { return "—" }

        return text
    }

    /// 顶部：appid、加载结果与当前在播的地址 / Top: appid, load result and the URL currently playing
    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("TrailerPlayerTestView · appid \(appID)")
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .foregroundStyle(.primaryText)

            if let name = details?.name {
                Text(name)
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundStyle(.primaryText)
            }

            Text(details == nil ? statusText : "movies \(details?.movies?.count ?? 0)")
                .font(.system(size: 13, weight: .regular, design: .rounded))
                .foregroundStyle(.secondaryText)

            Text(playingURL ?? "nothing playing")
                .font(.system(size: 11, weight: .regular))
                .foregroundStyle(.secondaryText)
        }
    }

    /// 播放器加一个每秒刷新一次的进度，用来确认片子真的在走 /
    /// The player plus a once-a-second clock, so playback can be confirmed to advance
    private var playerBox: some View {
        VStack(alignment: .leading, spacing: 8) {
            VideoPlayer(player: player)
                .frame(maxWidth: .infinity)
                .aspectRatio(16 / 9, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

            HStack(spacing: 12) {
                TimelineView(.periodic(from: .now, by: 1)) { _ in
                    let seconds = player.currentTime().seconds
                    let size = player.currentItem?.presentationSize ?? .zero

                    Text(
                        "t = \(seconds.isFinite ? String(format: "%.0f", seconds) : "—") s"
                            + " · rate \(player.rate)"
                            + " · playing \(Int(size.width))x\(Int(size.height))"
                    )
                    .font(.system(size: 13, weight: .regular))
                    .foregroundStyle(.brandGreen)
                }

                Spacer()

                qualityMenu
            }
        }
    }

    /// 清晰度菜单；换档位不重建 item，只改上限 / Changing quality only adjusts the cap, no new item
    private var qualityMenu: some View {
        Menu {
            Button("Auto") {
                selectedQuality = nil
                applyQuality()
            }

            ForEach(qualities) { quality in
                Button(quality.label) {
                    selectedQuality = quality.size
                    applyQuality()
                }
            }
        } label: {
            Label(selectedQuality.map { "\(Int($0.height))p" } ?? "Auto", systemImage: "slider.horizontal.3")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
        }
        .tint(.brandBlue)
    }

    /// 每个视频一行，点一下用共用播放器播它 / One row per movie; a tap plays it on the shared player
    private var movieRows: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array((details?.movies ?? []).enumerated()), id: \.offset) { _, movie in
                Button {
                    play(movie)
                } label: {
                    HStack(spacing: 12) {
                        AsyncImage(url: posterURL(movie)) { phase in
                            if let image = phase.image {
                                image.resizable().scaledToFill()
                            } else {
                                Color.appBackground
                            }
                        }
                        .frame(width: 96, height: 54)
                        .clipped()

                        VStack(alignment: .leading, spacing: 4) {
                            Text(movie.name ?? "Untitled")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                .foregroundStyle(.primaryText)
                                .multilineTextAlignment(.leading)

                            Text("hls \(movie.isPlayable ? "ok" : "none") · highlight \(movie.highlight == true ? "yes" : "no")")
                                .font(.system(size: 13, weight: .regular, design: .rounded))
                                .foregroundStyle(.secondaryText)
                        }

                        Spacer()

                        if playingURL == movie.preferredURL {
                            Image(systemName: "speaker.wave.2.fill")
                                .font(.system(size: 16, weight: .medium))
                                .foregroundStyle(.brandGreen)
                        }
                    }
                    .padding(10)
                    .background(
                        Color.surfaceElevated,
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    /// 拉真实数据并自动播主预告片 / Loads real data and autoplays the main trailer
    private func load() async {
        do {
            let loaded = try await SteamAPI.fetchAppDetails(appID: appID)
            details = loaded
            statusText = "loaded"

            if let trailer = loaded.movies?.mainTrailer {
                play(trailer)
            }
        } catch {
            statusText = "failed: \(error)"
        }
    }

    /// 把这条视频交给共用播放器 / Hands one movie to the shared player
    private func play(_ movie: SteamMovie) {
        guard let urlString = movie.preferredURL, let url = URL(string: urlString) else { return }

        player.replaceCurrentItem(with: AVPlayerItem(url: url))
        playingURL = urlString
        // 新 item 会带上它自己的默认值，上限每次都要重新应用 / A new item carries its own defaults
        applyQuality()
        player.play()

        Task { await loadQualities(of: movie) }
    }

    /// 读主清单里的档位，每个视频各有一份阶梯 / Reads the ladder; every movie carries its own
    private func loadQualities(of movie: SteamMovie) async {
        qualities = []

        guard let urlString = movie.preferredURL, let url = URL(string: urlString) else { return }

        let variants = (try? await AVURLAsset(url: url).load(.variants)) ?? []
        qualities = variants
            .compactMap { variant in
                guard let size = variant.videoAttributes?.presentationSize, size != .zero else { return nil }
                return Quality(size: size, peakBitRate: variant.peakBitRate ?? 0)
            }
            .sorted { $0.size.height > $1.size.height }
    }

    /// 应用清晰度上限，`.zero` 交回系统自适应 / Applies the cap; `.zero` hands control back to ABR
    private func applyQuality() {
        player.currentItem?.preferredMaximumResolution = selectedQuality ?? .zero
    }

    /// 封面大图：`thumbnail` 只有 293×165，换成同目录的 `movie_full.jpg` /
    /// The full-size cover: the sibling `movie_full.jpg` instead of the 293×165 thumbnail
    private func posterURL(_ movie: SteamMovie) -> URL? {
        guard let thumbnail = movie.thumbnail else { return nil }

        return URL(string: thumbnail.replacingOccurrences(of: "movie.293x165.jpg", with: "movie_full.jpg"))
    }
}

#Preview {
    TrailerPlayerTestView()
}
