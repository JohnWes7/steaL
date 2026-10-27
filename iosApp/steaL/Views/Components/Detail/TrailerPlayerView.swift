//
//  TrailerPlayerView.swift
//  steaL
//
//  Created by yijiang on 2026/9/19.
//

import AVKit
import SwiftUI

/// 画廊里原位播放的预告片：无系统控件的画面 + 自画控件，画面只响应点按，横滑交给翻页 /
/// A trailer played in place in the gallery: a control-free video layer plus our own controls;
/// the picture only takes taps, so horizontal swipes page the gallery
struct TrailerPlayerView: View {

    /// 共用的播放状态 / The shared playback state
    let trailer: TrailerPlayer

    var body: some View {
        ZStack {
            Color.appBackground

            PlayerLayerView(player: trailer.player)

            statusOverlay
        }
        .overlay(alignment: .topLeading) {
            closeButton
        }
        .overlay(alignment: .bottom) {
            // 失败时播放、拖动、全屏都没意义 / Play, scrub and full screen mean nothing after a failure
            if trailer.status != .failed {
                controlBar
            }
        }
        // 播放器始终是暗色画面，token 一律按 dark 取 / The player is always a dark surface
        .environment(\.colorScheme, .dark)
    }

    /// 画面中间：缓冲时转圈，失败时提示加重试。
    /// The middle of the picture: a spinner while buffering, a message and retry after a failure.
    @ViewBuilder
    private var statusOverlay: some View {
        switch trailer.status {
        case .buffering:
            ProgressView()
                .tint(.brandGreen)
        case .failed:
            failurePrompt
        case .paused, .playing:
            EmptyView()
        }
    }

    /// 加载失败的提示。
    /// The message shown when the video fails to load.
    ///
    /// 重试不用玻璃按钮：点了就会被移除，之后新建的玻璃控件会带着按压放大残留。
    /// Retry is not a glass button: it disappears on tap, which would leave the next glass controls stuck enlarged.
    private var failurePrompt: some View {
        VStack(spacing: 12) {
            Image(systemName: "play.slash")
                .font(.system(.title2, weight: .semibold))
                .foregroundStyle(.secondaryText)

            Text("Couldn't play this trailer.")
                .font(.system(.subheadline, design: .rounded, weight: .semibold))
                .foregroundStyle(.primaryText)

            Button("Retry", action: trailer.retry)
                .font(.system(.subheadline, design: .rounded, weight: .semibold))
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
                .tint(.brandBlue)
        }
        .padding()
    }

    /// 回到封面 / Returns to the poster
    private var closeButton: some View {
        Button(action: trailer.stop) {
            Image(systemName: "xmark")
                .font(.system(.subheadline, weight: .bold))
                .frame(width: 24, height: 24)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .accessibilityLabel("Close trailer")
        .padding(8)
    }

    /// 底部控件栏：播放 / 进度条 / 时间 / 全屏 / The bottom bar: play, scrubber, time, full screen
    private var controlBar: some View {
        HStack(spacing: 10) {
            Button(action: trailer.togglePlayPause) {
                Image(systemName: trailer.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(.subheadline, weight: .bold))
                    .frame(width: 24, height: 24)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .accessibilityLabel(trailer.isPlaying ? "Pause" : "Play")
            // 只有「播放」会开始播放；VoiceOver 随后不说话，不盖住预告片的声音 /
            // Only Play starts playback; VoiceOver then stays quiet so it doesn't talk over the trailer
            .accessibilityAddTraits(trailer.isPlaying ? [] : .startsMediaSession)

            TrailerScrubber(
                progress: trailer.progress,
                isScrubbing: trailer.isScrubbing,
                valueText: timeText,
                onScrub: trailer.scrub(to:),
                onScrubEnd: trailer.endScrub,
                onSkip: trailer.skip(by:)
            )

            Text(timeText)
                .font(.system(.caption, design: .rounded, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(.primaryText)
                // 进度条的 value 已经读出同样的时间 / The scrubber's value already reads the same time
                .accessibilityHidden(true)

            Button {
                trailer.isFullScreen = true
            } label: {
                Image(systemName: "arrow.up.left.and.arrow.down.right")
                    .font(.system(.subheadline, weight: .bold))
                    .frame(width: 24, height: 24)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .accessibilityLabel("Full screen")
        }
        .padding(.horizontal, 8)
        .padding(.bottom, 4)
        // 渐变只为压住亮画面，不拦触摸 / The scrim only darkens bright frames and takes no touches
        .background {
            LinearGradient(
                colors: [.clear, Color.appBackground.opacity(0.8)],
                startPoint: .top,
                endPoint: .bottom
            )
            .padding(.top, -20)
            .allowsHitTesting(false)
        }
    }

    /// 「0:42 / 1:30」，时长未知时只显示当前位置 / "0:42 / 1:30"; only the position until the length is known
    private var timeText: String {
        let current = Self.format(trailer.currentTime)
        guard trailer.duration > 0 else { return current }
        return "\(current) / \(Self.format(trailer.duration))"
    }

    private static func format(_ seconds: Double) -> String {
        Duration.seconds(seconds.isFinite ? seconds : 0).formatted(.time(pattern: .minuteSecond))
    }
}

// MARK: - 进度条 / Scrubber

/// 进度条：可见轨道很细，命中区 44pt 高。
/// The scrubber: a thin visible track inside a 44pt hit area.
///
/// 拖动手势按下即成立，比画廊的横滑（要移动约 10pt）先成立，所以拖进度条不会翻页，画廊也不用 `.scrollDisabled`。
/// The drag is recognised on touch-down, before the gallery's swipe, which needs about 10pt of travel,
/// so scrubbing never pages and the gallery needs no `.scrollDisabled`.
private struct TrailerScrubber: View {

    let progress: Double
    let isScrubbing: Bool
    /// 给 VoiceOver 读的当前位置 / The position read out by VoiceOver
    let valueText: String
    let onScrub: (Double) -> Void
    let onScrubEnd: () -> Void
    let onSkip: (Double) -> Void

    @State private var width: CGFloat = 0

    var body: some View {
        Capsule()
            .fill(Color.secondaryText.opacity(0.4))
            .overlay(alignment: .leading) {
                Capsule()
                    .fill(.primaryText)
                    .frame(width: width * progress)
            }
            .frame(height: isScrubbing ? 8 : 4)
            .frame(maxWidth: .infinity, minHeight: 44)
            .contentShape(.rect)
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.size.width
            } action: { newWidth in
                width = newWidth
            }
            // `minimumDistance: 0`：按下即触发，比翻页手势（要移动约 10pt）早 /
            // Fires on touch-down, before the page swipe, which needs about 10pt of travel
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        guard width > 0 else { return }
                        onScrub(value.location.x / width)
                    }
                    .onEnded { _ in
                        onScrubEnd()
                    }
            )
            .animation(.snappy(duration: 0.2), value: isScrubbing)
            .accessibilityElement()
            .accessibilityLabel("Playback position")
            .accessibilityValue(valueText)
            // 播放时 value 每秒都在变 / The value changes every second during playback
            .accessibilityAddTraits(.updatesFrequently)
            .accessibilityAdjustableAction { direction in
                switch direction {
                case .increment:
                    onSkip(10)
                case .decrement:
                    onSkip(-10)
                @unknown default:
                    break
                }
            }
    }
}

// MARK: - 画面层 / Video layer

/// 不带任何系统控件的播放画面 / The video picture without any system controls
private struct PlayerLayerView: UIViewRepresentable {

    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerLayerUIView {
        let view = PlayerLayerUIView()
        view.playerLayer?.videoGravity = .resizeAspect
        view.playerLayer?.player = player
        return view
    }

    func updateUIView(_ uiView: PlayerLayerUIView, context: Context) {
        uiView.playerLayer?.player = player
    }
}

/// 根 layer 就是 `AVPlayerLayer`，尺寸随视图自动变 / The backing layer is an `AVPlayerLayer`, so it resizes with the view
private final class PlayerLayerUIView: UIView {

    override static var layerClass: AnyClass { AVPlayerLayer.self }

    var playerLayer: AVPlayerLayer? { layer as? AVPlayerLayer }
}

#Preview("内嵌播放 / Inline") {
    @Previewable @State var trailer = TrailerPlayer()

    TrailerPlayerView(trailer: trailer)
        .aspectRatio(16 / 9, contentMode: .fit)
        .task {
            trailer.play(.previewSample)
        }
}

#Preview("加载失败 / Failed") {
    @Previewable @State var trailer = TrailerPlayer()

    TrailerPlayerView(trailer: trailer)
        .aspectRatio(16 / 9, contentMode: .fit)
        .task {
            trailer.play(.previewBrokenSample)
        }
}
