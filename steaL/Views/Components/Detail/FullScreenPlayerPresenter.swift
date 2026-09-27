//
//  FullScreenPlayerPresenter.swift
//  steaL
//
//  Created by yijiang on 2026/9/28.
//

import AVKit
import SwiftUI

extension View {

    /// 模态弹出系统全屏播放器，写法同 `fullScreenCover(isPresented:)`。
    /// Presents the system full-screen player modally, in the style of `fullScreenCover(isPresented:)`.
    ///
    /// 系统播放器直接模态弹出才是真全屏：画面铺满、控件自动隐藏，关闭、投屏、画中画都由它自己提供。
    /// 放进 `fullScreenCover` 再套导航栏会被导航栏和安全区挤小。
    /// Only a modally presented system player is truly full screen: the picture fills the display, the
    /// controls hide themselves, and it brings its own close, AirPlay and Picture in Picture buttons.
    /// Wrapping it in a `fullScreenCover` with a navigation bar shrinks it into the safe area.
    ///
    /// - Parameters:
    ///   - isPresented: 是否在全屏播放，用户关掉后置回 `false` / Whether the full-screen player shows; reset to `false` when the person closes it.
    ///   - player: 要播放的播放器，与内嵌画面共用 / The player to show, shared with the inline view.
    func fullScreenPlayer(isPresented: Binding<Bool>, player: AVPlayer) -> some View {
        background {
            FullScreenPlayerPresenter(isPresented: isPresented, player: player)
        }
    }
}

/// 不占位置的桥接视图，负责弹出和收起 `AVPlayerViewController`。
/// A zero-footprint bridge that presents and dismisses an `AVPlayerViewController`.
private struct FullScreenPlayerPresenter: UIViewControllerRepresentable {

    /// 是否在全屏播放 / Whether the full-screen player shows.
    @Binding var isPresented: Bool

    /// 要播放的播放器 / The player to show.
    let player: AVPlayer

    func makeCoordinator() -> Coordinator {
        Coordinator(isPresented: $isPresented)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        UIViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        let coordinator = context.coordinator
        coordinator.isPresented = $isPresented

        let isShowing = coordinator.playerViewController != nil
        if isPresented, !isShowing {
            coordinator.present(player, from: uiViewController)
        } else if !isPresented, isShowing {
            coordinator.dismiss()
        }
    }

    /// 持有弹出的播放器，并在用户关掉它时把绑定置回 `false`。
    /// Holds the presented player and resets the binding when the person closes it.
    final class Coordinator: NSObject, AVPlayerViewControllerDelegate {

        /// 最新的绑定，每次 `updateUIViewController` 刷新 / The latest binding, refreshed on every update.
        var isPresented: Binding<Bool>

        /// 正在显示的全屏播放器，`nil` = 没在全屏 / The player on screen; `nil` means not full screen.
        private(set) var playerViewController: AVPlayerViewController?

        init(isPresented: Binding<Bool>) {
            self.isPresented = isPresented
        }

        /// 模态弹出全屏播放器。
        /// Presents the full-screen player modally.
        ///
        /// - Parameters:
        ///   - player: 要播放的播放器 / The player to show.
        ///   - presenter: 负责弹出的视图控制器 / The view controller that presents it.
        func present(_ player: AVPlayer, from presenter: UIViewController) {
            let playerViewController = AVPlayerViewController()
            playerViewController.player = player
            playerViewController.delegate = self
            self.playerViewController = playerViewController
            presenter.present(playerViewController, animated: true)
        }

        /// 代码里收起全屏播放器（比如停播时）。
        /// Dismisses the full-screen player from code, such as when playback stops.
        func dismiss() {
            playerViewController?.dismiss(animated: true)
            playerViewController = nil
        }

        /// 用户点关闭或下滑收起时回调；转场没被取消才算真的关了。
        /// Called when the person closes or swipes the player away; it only counts once the transition is not cancelled.
        func playerViewController(
            _ playerViewController: AVPlayerViewController,
            willEndFullScreenPresentationWithAnimationCoordinator coordinator: UIViewControllerTransitionCoordinator
        ) {
            coordinator.animate(alongsideTransition: nil) { context in
                guard !context.isCancelled else { return }

                self.playerViewController = nil
                self.isPresented.wrappedValue = false
            }
        }
    }
}

#Preview {
    @Previewable @State var trailer = TrailerPlayer()

    Button("Play Full Screen") {
        trailer.play(.previewSample)
        trailer.isFullScreen = true
    }
    .fullScreenPlayer(isPresented: $trailer.isFullScreen, player: trailer.player)
}
