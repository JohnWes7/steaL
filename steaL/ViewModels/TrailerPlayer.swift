//
//  TrailerPlayer.swift
//  steaL
//
//  Created by yijiang on 2026/9/19.
//

import AVFoundation

/// 画廊预告片的播放状态：全画廊共用一个 `AVPlayer`，进度条拖动期间 `isScrubbing` 为真，画廊据此锁住翻页 /
/// Playback state for the gallery trailer; one `AVPlayer` serves the whole gallery, and `isScrubbing`
/// tells the gallery to lock paging while the scrubber is dragged
@MainActor
@Observable
final class TrailerPlayer {

    /// 全画廊共用的播放器 / The player shared by the whole gallery
    let player = AVPlayer()

    /// 正在播的那条，`nil` = 所有页都停在封面 / The movie being played; `nil` means every page shows its poster
    private(set) var movie: SteamMovie?
    /// 当前播放位置（秒），拖动时跟手 / Current position in seconds; follows the finger while scrubbing
    private(set) var currentTime: Double = 0
    /// 总时长（秒），HLS 加载出来之前是 0 / Total length in seconds; 0 until the HLS playlist loads
    private(set) var duration: Double = 0
    /// 想要播放（含缓冲中），决定显示暂停还是播放图标 / Wants to play, buffering included; picks the pause or play icon
    private(set) var isPlaying = false
    /// 缓冲中 / Waiting for data
    private(set) var isBuffering = false
    /// 手指按在进度条上 / A finger is on the scrubber
    private(set) var isScrubbing = false
    /// 是否在系统全屏播放器里 / Whether the system full-screen player is showing
    var isFullScreen = false

    @ObservationIgnored private var timeObserver: Any?
    @ObservationIgnored private var statusObservation: NSKeyValueObservation?
    /// 拖动前在播，松手后要接着播 / Was playing before the drag, so resume on release
    @ObservationIgnored private var resumesAfterScrub = false
    /// 在飞的 seek 完成前到来的最新目标 / The latest target that arrived while a seek was in flight
    @ObservationIgnored private var pendingSeek: CMTime?
    @ObservationIgnored private var isSeeking = false

    /// 进度 0…1 / Progress from 0 to 1
    var progress: Double {
        guard duration > 0 else { return 0 }
        return min(max(currentTime / duration, 0), 1)
    }

    /// 换 item 并起播 / Swaps the item and starts playback
    func play(_ movie: SteamMovie) {
        guard let urlString = movie.preferredURL, let url = URL(string: urlString) else { return }

        stop()
        player.replaceCurrentItem(with: AVPlayerItem(url: url))
        self.movie = movie
        observePlayer()
        player.play()
    }

    /// 停播、释放当前视频并回到封面 / Stops playback, releases the current video and returns to the poster
    func stop() {
        guard movie != nil else { return }

        player.pause()
        player.replaceCurrentItem(with: nil)
        if let timeObserver {
            player.removeTimeObserver(timeObserver)
        }
        timeObserver = nil
        statusObservation?.invalidate()
        statusObservation = nil

        movie = nil
        currentTime = 0
        duration = 0
        isPlaying = false
        isBuffering = false
        isScrubbing = false
        isFullScreen = false
        resumesAfterScrub = false
        pendingSeek = nil
    }

    /// 播放 / 暂停；停在结尾时从头重播 / Plays or pauses; replays from the start when parked at the end
    func togglePlayPause() {
        guard player.rate == 0 else {
            player.pause()
            return
        }

        if duration > 0, currentTime >= duration - 0.5 {
            player.seek(to: .zero)
        }
        player.play()
    }

    /// 跳过若干秒，给 VoiceOver 的调节动作用 / Skips by some seconds, for the VoiceOver adjustable action
    func skip(by seconds: Double) {
        guard duration > 0 else { return }

        currentTime = min(max(currentTime + seconds, 0), duration)
        player.seek(to: CMTime(seconds: currentTime, preferredTimescale: 600))
    }

    // MARK: - 拖动进度条 / Scrubbing

    /// 手指在进度条上的位置（0…1）；第一次调用时暂停并锁住翻页 /
    /// The finger's position on the scrubber, 0 to 1; the first call pauses and locks paging
    func scrub(to fraction: Double) {
        guard duration > 0 else { return }

        if !isScrubbing {
            isScrubbing = true
            resumesAfterScrub = player.rate != 0
            player.pause()
        }

        currentTime = min(max(fraction, 0), 1) * duration
        pendingSeek = CMTime(seconds: currentTime, preferredTimescale: 600)
        seekToPendingTarget()
    }

    /// 松手：解锁翻页，最后一次 seek 完成后按需恢复播放 /
    /// Finger lifted; unlocks paging and resumes, if needed, once the last seek lands
    func endScrub() {
        guard isScrubbing else { return }

        isScrubbing = false
        if !isSeeking {
            finishScrub()
        }
    }

    /// 追赶式 seek（Apple QA1820）：上一次没完成就只记最新目标，完成后再追，拖动才不卡 /
    /// Chase seeking (Apple QA1820): only the newest target is kept while a seek is in flight
    private func seekToPendingTarget() {
        guard !isSeeking, let target = pendingSeek else { return }

        isSeeking = true
        pendingSeek = nil
        player.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
            // 先解包成常量，`Task` 不能引用外层捕获的 weak var / Unwrap first; the `Task` must not touch the captured weak var
            guard let self else { return }
            Task { @MainActor in
                self.seekDidFinish()
            }
        }
    }

    /// 一次 seek 落地 / One seek has landed
    private func seekDidFinish() {
        isSeeking = false

        if pendingSeek != nil {
            seekToPendingTarget()
        } else if !isScrubbing {
            finishScrub()
        }
    }

    /// 拖动整个结束 / The whole drag is over
    private func finishScrub() {
        if resumesAfterScrub, movie != nil {
            player.play()
        }
        resumesAfterScrub = false
    }

    // MARK: - 观察播放器 / Observing the player

    /// 进度与播放状态的观察，`stop()` 里移除 / Time and status observation, removed in `stop()`
    private func observePlayer() {
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            MainActor.assumeIsolated {
                self?.syncTime(time)
            }
        }

        statusObservation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] player, _ in
            guard let self else { return }
            let status = player.timeControlStatus
            Task { @MainActor in
                self.syncStatus(status)
            }
        }
    }

    /// 拖动或 seek 在飞时不覆盖，否则进度条会跳回旧位置 / Skipped while scrubbing or seeking, or the bar jumps back
    private func syncTime(_ time: CMTime) {
        if let itemDuration = player.currentItem?.duration.seconds,
            itemDuration.isFinite, itemDuration > 0, itemDuration != duration {
            duration = itemDuration
        }

        guard !isScrubbing, !isSeeking, time.seconds.isFinite else { return }
        currentTime = time.seconds
    }

    /// 拖动期间的暂停是临时的，不翻成播放图标 / The pause during a drag is temporary, so the icon stays
    private func syncStatus(_ status: AVPlayer.TimeControlStatus) {
        guard movie != nil, !isScrubbing, !isSeeking else { return }

        isPlaying = status != .paused
        isBuffering = status == .waitingToPlayAtSpecifiedRate
    }
}
