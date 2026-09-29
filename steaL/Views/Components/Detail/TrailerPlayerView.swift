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

            if trailer.isBuffering {
                ProgressView()
                    .tint(.brandGreen)
            }
        }
        .overlay(alignment: .topLeading) {
            closeButton
        }
        .overlay(alignment: .bottom) {
            controlBar
        }
        // 播放器始终是暗色画面，token 一律按 dark 取 / The player is always a dark surface
        .environment(\.colorScheme, .dark)
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

/// 进度条：可见轨道很细，命中区 44pt 高。手指一按下就开始拖，画廊借此在翻页手势识别之前锁住翻页 /
/// The scrubber: a thin visible track inside a 44pt hit area. The drag starts on touch-down, which lets
/// the gallery lock paging before its swipe is recognised
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
