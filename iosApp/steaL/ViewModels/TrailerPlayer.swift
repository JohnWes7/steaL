//
//  TrailerPlayer.swift
//  steaL
//
//  Created by yijiang on 2026/9/19.
//

import AVFoundation
import AVKit
import OSLog

/// 画廊预告片的播放状态，全画廊共用一个 `AVPlayer`。
/// Playback state for the gallery trailer; one `AVPlayer` serves the whole gallery.
///
/// `AVPlayer` 不是 `@Observable`，它的状态只通过 KVO 通知，SwiftUI 看不到。这个类观察它，把界面要的状态抄成自己的属性；
/// 另外管 `AVPlayer` 没有的东西：播的是哪一条、全屏与否、拖动进度条。
/// `AVPlayer` is not `@Observable` and reports changes only through KVO, which SwiftUI cannot see. This class observes it
/// and copies what the interface needs into its own properties; it also owns what `AVPlayer` lacks: which movie is
/// playing, full screen, and scrubbing.
///
/// `isScrubbing` 只管拖动期间的显示：进度条变粗，`status` 和 `currentTime` 不被播放器的回调覆盖。翻页不靠它锁，
/// 进度条的拖动手势按下即成立，画廊的横滑就不会开始。
/// `isScrubbing` only drives what shows during a drag: a thicker bar, and a `status` and `currentTime` that the
/// player's callbacks leave alone. Paging is not locked by it; the scrubber's drag is recognised on touch-down,
/// so the gallery's swipe never begins.
@MainActor
@Observable
final class TrailerPlayer {

    /// 界面要的播放状态，由 `AVPlayerItem.status` 和 `AVPlayer.timeControlStatus` 合成。
    /// The playback status the interface needs, combined from `AVPlayerItem.status` and `AVPlayer.timeControlStatus`.
    ///
    /// 每次 `play(_:)` 都新建 item，item 的寿命就是「这一条视频」，所以两个状态描述的是同一件事，合成一个给界面 `switch`。
    /// Every `play(_:)` builds a new item, so the item lives exactly as long as the movie; both statuses describe the
    /// same thing and are combined into one for the interface to switch on.
    enum Status {
        /// 停着：用户暂停、播到结尾或还没开始 / Stopped: paused by the person, at the end, or not started.
        case paused
        /// 想播但在等数据（`.waitingToPlayAtSpecifiedRate`）/ Wants to play but is waiting for data.
        case buffering
        /// 正在出画面 / The picture is moving.
        case playing
        /// 这条视频加载失败（item 的 `.failed`），只能 `retry()` 重新加载 / This video failed to load; only `retry()` reloads it.
        case failed
    }

    /// 全画廊共用的播放器，公开只为给画面层和系统全屏播放器显示。
    /// The player shared by the whole gallery, exposed only so the video layer and the system full-screen player can show it.
    let player = AVPlayer()

    /// 正在播的那条，`nil` = 所有页都停在封面 / The movie being played; `nil` means every page shows its poster.
    private(set) var movie: SteamMovie?
    /// 播放状态 / The playback status.
    private(set) var status: Status = .paused
    /// 当前播放位置（秒），拖动时跟手 / Current position in seconds; follows the finger while scrubbing.
    private(set) var currentTime: Double = 0
    /// 总时长（秒），HLS 加载出来之前是 0 / Total length in seconds; 0 until the HLS playlist loads.
    private(set) var duration: Double = 0
    /// 手指按在进度条上 / A finger is on the scrubber.
    private(set) var isScrubbing = false
    /// 是否在系统全屏播放器（模态弹出的 `AVPlayerViewController`）里 /
    /// Whether the system full-screen player (a modally presented `AVPlayerViewController`) is showing.
    var isFullScreen = false

    /// 每 0.25 秒报一次进度 / Reports the position every 0.25 seconds.
    @ObservationIgnored private var timeObserver: Any?
    /// 观察「这条视频能不能播」/ Observes whether this video can play.
    @ObservationIgnored private var itemStatusObservation: NSKeyValueObservation?
    /// 观察「播放器在不在播」/ Observes whether the player is playing.
    @ObservationIgnored private var timeControlObservation: NSKeyValueObservation?
    /// 拖动前在播，松手后要接着播 / Was playing before the drag, so resume on release.
    @ObservationIgnored private var resumesAfterScrub = false
    /// 在飞的 seek 完成前到来的最新目标 / The latest target that arrived while a seek was in flight.
    @ObservationIgnored private var pendingSeek: CMTime?
    /// 有一次 seek 还没完成 / A seek has not landed yet.
    @ObservationIgnored private var isSeeking = false

    /// 进度 0…1 / Progress from 0 to 1.
    var progress: Double {
        guard duration > 0 else { return 0 }
        return min(max(currentTime / duration, 0), 1)
    }

    /// 显示暂停图标还是播放图标：缓冲中也算在播。
    /// Whether to show the pause icon rather than the play icon; buffering counts as playing.
    var isPlaying: Bool {
        switch status {
        case .buffering, .playing:
            return true
        case .paused, .failed:
            return false
        }
    }

    // MARK: - 播放与停止 / Playing and stopping

    /// 换成这条视频并起播。
    /// Swaps in this movie and starts playback.
    ///
    /// - Parameter movie: 要播的视频 / The movie to play.
    func play(_ movie: SteamMovie) {
        guard let urlString = movie.preferredURL, let url = URL(string: urlString) else { return }

        stop()
        let item = AVPlayerItem(url: url)
        // 系统全屏播放器从这里读标题 / The system full-screen player reads its title from here
        if let name = movie.name {
            let titleItem = AVMutableMetadataItem()
            titleItem.identifier = .commonIdentifierTitle
            titleItem.value = name as NSString
            titleItem.extendedLanguageTag = "und"
            item.externalMetadata = [titleItem]
        }
        player.replaceCurrentItem(with: item)
        self.movie = movie
        // 刚建的 item 还要加载，先显示转圈，之后由观察回调纠正 /
        // A new item still has to load, so show the spinner until the observers correct it
        status = .buffering
        startObserving(item)
        player.play()
    }

    /// 失败后重新加载同一条视频。
    /// Reloads the same movie after a failure.
    ///
    /// 失败的 item 不能再播，只能新建一个，`play(_:)` 本来就会这样做。
    /// A failed item can never play again, so a new one is needed, which `play(_:)` already builds.
    func retry() {
        guard let movie else { return }

        play(movie)
    }

    /// 停播、释放当前视频并回到封面。
    /// Stops playback, releases the current video and returns to the poster.
    func stop() {
        guard movie != nil else { return }

        stopObserving()
        player.pause()
        player.replaceCurrentItem(with: nil)

        movie = nil
        status = .paused
        currentTime = 0
        duration = 0
        isScrubbing = false
        isFullScreen = false
        resumesAfterScrub = false
        pendingSeek = nil
    }

    /// 播放 / 暂停；停在结尾时从头重播。
    /// Plays or pauses; replays from the start when parked at the end.
    func togglePlayPause() {
        guard status != .failed else { return }

        // `rate` 是「要它播」，不是「它在播」：缓冲中 `rate` 也不是 0，点了就暂停 /
        // `rate` means "asked to play", not "is playing": it is non-zero while buffering, so a tap pauses
        guard player.rate == 0 else {
            player.pause()
            return
        }

        if duration > 0, currentTime >= duration - 0.5 {
            player.seek(to: .zero)
        }
        player.play()
    }

    /// 跳过若干秒，给 VoiceOver 的调节动作用。
    /// Skips by some seconds, for the VoiceOver adjustable action.
    ///
    /// - Parameter seconds: 正数快进，负数后退 / Positive skips forward, negative skips back.
    func skip(by seconds: Double) {
        guard duration > 0 else { return }

        currentTime = min(max(currentTime + seconds, 0), duration)
        player.seek(to: CMTime(seconds: currentTime, preferredTimescale: 600))
    }

    // MARK: - 拖动进度条 / Scrubbing

    /// 手指在进度条上的位置；第一次调用时暂停。
    /// The finger's position on the scrubber; the first call pauses.
    ///
    /// - Parameter fraction: 0…1 的位置 / The position from 0 to 1.
    func scrub(to fraction: Double) {
        guard duration > 0, status != .failed else { return }

        if !isScrubbing {
            isScrubbing = true
            resumesAfterScrub = player.rate != 0
            player.pause()
        }

        currentTime = min(max(fraction, 0), 1) * duration
        pendingSeek = CMTime(seconds: currentTime, preferredTimescale: 600)
        seekToPendingTarget()
    }

    /// 松手；最后一次 seek 完成后按需恢复播放。
    /// Finger lifted; resumes, if needed, once the last seek lands.
    func endScrub() {
        guard isScrubbing else { return }

        isScrubbing = false
        if !isSeeking {
            finishScrub()
        }
    }

    /// 追赶式 seek（Apple QA1820）：上一次没完成就只记最新目标，完成后再追，拖动才不卡。
    /// Chase seeking (Apple QA1820): only the newest target is kept while a seek is in flight, so dragging stays smooth.
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

    /// 一次 seek 落地 / One seek has landed.
    private func seekDidFinish() {
        isSeeking = false

        if pendingSeek != nil {
            seekToPendingTarget()
        } else if !isScrubbing {
            finishScrub()
        }
    }

    /// 拖动整个结束：拖之前在播就接着播，`status` 由观察回调跟上。
    /// The whole drag is over: resumes if it was playing before, and the observers bring `status` up to date.
    private func finishScrub() {
        if resumesAfterScrub, movie != nil, status != .failed {
            player.play()
        }
        resumesAfterScrub = false
    }

    // MARK: - 观察播放器 / Observing the player

    /// 开始观察进度、这条视频能不能播、播放器在不在播。
    /// Starts observing the position, whether this video can play, and whether the player is playing.
    ///
    /// KVO 回调可能不在主线程，所以回调只负责「通知有变化」，回到主线程后由 `syncStatus()` 读播放器现在的真实值。
    /// 这样也不会被已经换掉的旧 item 晚到的回调带偏。
    /// KVO callbacks may arrive off the main thread, so they only signal that something changed; back on the main
    /// actor `syncStatus()` reads the player's current values, which also ignores late callbacks from a replaced item.
    ///
    /// - Parameter item: 刚换上的视频 / The item just swapped in.
    private func startObserving(_ item: AVPlayerItem) {
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            MainActor.assumeIsolated {
                self?.syncTime(time)
            }
        }

        itemStatusObservation = item.observe(\.status, options: [.new]) { [weak self] _, _ in
            guard let self else { return }
            Task { @MainActor in
                self.syncStatus()
            }
        }

        timeControlObservation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] _, _ in
            guard let self else { return }
            Task { @MainActor in
                self.syncStatus()
            }
        }
    }

    /// 移除所有观察 / Removes every observer.
    private func stopObserving() {
        if let timeObserver {
            player.removeTimeObserver(timeObserver)
        }
        timeObserver = nil
        itemStatusObservation?.invalidate()
        itemStatusObservation = nil
        timeControlObservation?.invalidate()
        timeControlObservation = nil
    }

    /// 同步进度与时长；拖动或 seek 在飞时不覆盖位置，否则进度条会跳回旧位置。
    /// Syncs the position and length; the position is left alone while scrubbing or seeking, or the bar jumps back.
    ///
    /// - Parameter time: 播放器报的当前位置 / The position the player reported.
    private func syncTime(_ time: CMTime) {
        if let itemDuration = player.currentItem?.duration.seconds,
            itemDuration.isFinite, itemDuration > 0, itemDuration != duration {
            duration = itemDuration
        }

        guard !isScrubbing, !isSeeking, time.seconds.isFinite else { return }
        currentTime = time.seconds
    }

    /// 按播放器现在的真实状态算出 `status`。
    /// Works out `status` from the player's current state.
    ///
    /// 顺序就是优先级：已停播或已失败不再改；item 失败马上显示，拖动中也一样；拖动期间的暂停是临时的，不改成 `.paused`；
    /// 其余照 `timeControlStatus` 换算。
    /// The order is the priority: nothing changes once stopped or failed; an item failure shows at once, even mid-drag;
    /// the pause during a drag is temporary and does not become `.paused`; otherwise `timeControlStatus` decides.
    private func syncStatus() {
        guard movie != nil, status != .failed else { return }

        let itemStatus = player.currentItem?.status
        if itemStatus == .failed {
            markFailed()
            return
        }

        guard !isScrubbing, !isSeeking else { return }

        switch player.timeControlStatus {
        case .paused:
            status = .paused
        case .waitingToPlayAtSpecifiedRate:
            status = .buffering
        case .playing:
            status = .playing
        @unknown default:
            status = .paused
        }
    }

    /// 标记失败：停下播放器、放弃拖动，原因进日志。
    /// Marks the failure: stops the player, abandons any drag and logs the reason.
    private func markFailed() {
        let error = player.currentItem?.error
        let errorText = error.map { error in String(describing: error) } ?? "nil"
        Logger.gameDetail.error("预告片加载失败 / Trailer failed to load: \(errorText)")

        player.pause()
        status = .failed
        isScrubbing = false
        resumesAfterScrub = false
        pendingSeek = nil
    }
}
