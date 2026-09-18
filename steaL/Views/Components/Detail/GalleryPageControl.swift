//
//  GalleryPageControl.swift
//  steaL
//
//  Created by yijiang on 2026/9/19.
//

import SwiftUI
import UIKit

/// 画廊下方的原生分页点：放在画面外面，不会压住视频；点一下或沿着点拖动都能翻页，页数多时系统自动缩点 /
/// Native page dots below the gallery, outside the picture so they never cover a video; tapping or
/// dragging along them pages, and the system shrinks them for long galleries
struct GalleryPageControl: UIViewRepresentable {

    /// 总页数 / Total page count
    let numberOfPages: Int
    /// 当前页，与画廊 `ScrollView` 的 `scrollPosition` 双向绑定 / The current page, bound both ways with the gallery's `scrollPosition`
    @Binding var currentPage: Int?

    func makeUIView(context: Context) -> UIPageControl {
        let control = UIPageControl()
        control.backgroundStyle = .minimal
        control.pageIndicatorTintColor = .secondaryText.withAlphaComponent(0.4)
        control.currentPageIndicatorTintColor = .primaryText
        control.addTarget(
            context.coordinator,
            action: #selector(Coordinator.pageChanged(_:)),
            for: .valueChanged
        )
        return control
    }

    func updateUIView(_ control: UIPageControl, context: Context) {
        context.coordinator.currentPage = $currentPage
        control.numberOfPages = numberOfPages
        let page = currentPage ?? 0
        if control.currentPage != page {
            control.currentPage = page
        }
    }

    /// 撑满可用宽度，点在中间；高度用控件自己的 / Fills the offered width with the dots centred; height is the control's own
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UIPageControl, context: Context) -> CGSize? {
        let intrinsic = uiView.intrinsicContentSize
        return CGSize(width: proposal.width ?? intrinsic.width, height: intrinsic.height)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(currentPage: $currentPage)
    }

    /// 把 UIKit 的 `valueChanged` 转回 binding / Forwards UIKit's `valueChanged` into the binding
    final class Coordinator: NSObject {

        var currentPage: Binding<Int?>

        init(currentPage: Binding<Int?>) {
            self.currentPage = currentPage
        }

        @objc func pageChanged(_ control: UIPageControl) {
            withAnimation {
                currentPage.wrappedValue = control.currentPage
            }
        }
    }
}

#Preview("分页点 / Page dots") {
    @Previewable @State var page: Int? = 2

    VStack(spacing: 20) {
        GalleryPageControl(numberOfPages: 6, currentPage: $page)
        GalleryPageControl(numberOfPages: 24, currentPage: $page)
        Text("Page \((page ?? 0) + 1)")
            .foregroundStyle(.primaryText)
    }
    .padding()
    .background(Color.appBackground)
}
