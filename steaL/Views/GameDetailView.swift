//
//  GameDetailView.swift
//  steaL
//
//  Created by yijiang on 2026/9/12.
//

import SwiftUI

/// 游戏详情页 / Game detail page
///
/// 本轮**只显示传进来的参数**，用来验证导航链路。真正的数据加载与 UI 在后续步骤补 /
/// This round only renders the parameters it was handed, to prove the navigation works.
/// Real data loading and layout come next
struct GameDetailView: View {

    /// 导航携带的参数 / The payload handed over by the navigation stack
    let route: GameDetailRoute

    var body: some View {
        ZStack {
            Color.appBackground
                .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 18) {
                header

                parameterList

                Spacer()
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        // 来源页把导航栏藏起来了（`.toolbar(.hidden, for: .navigationBar)`），
        // 不在这里显式恢复的话详情页会连返回按钮一起没有 /
        // The source screen hides the navigation bar, so it has to be re-enabled here or the
        // detail page would also lose its back button
        .toolbar(.visible, for: .navigationBar)
        .navigationBarTitleDisplayMode(.inline)
    }

    /// 标题区，直接用列表传过来的名字，避免白屏 /
    /// Title block; uses the name handed over from the list so the screen is never blank
    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("GAME DETAIL")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.brandGreen)

            Text(route.title)
                .font(.system(size: 30, weight: .heavy, design: .rounded))
                .foregroundStyle(.primaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// 参数回显。`uuid` 为空时显式标注，方便区分「主页路径」和「搜索路径」/
    /// Parameter echo. A missing `uuid` is spelled out so the home path and the search path are
    /// easy to tell apart
    private var parameterList: some View {
        VStack(alignment: .leading, spacing: 12) {
            row(label: "appID", value: String(route.appID), isMissing: false)
            row(label: "uuid", value: route.uuid ?? "—", isMissing: route.uuid == nil)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    /// 单行参数 / One parameter line
    private func row(label: String, value: String, isMissing: Bool) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(label)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.secondaryText)
                .frame(width: 64, alignment: .leading)

            Text(value)
                .font(.system(size: 16, weight: .medium, design: .monospaced))
                .foregroundStyle(isMissing ? .secondaryText : .primaryText)
                .textSelection(.enabled)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

#Preview("主页路径 / Home path") {
    NavigationStack {
        GameDetailView(
            route: GameDetailRoute(
                appID: 1245620,
                uuid: "018d937f-590c-728b-ac35-38bcff85f086",
                title: "ELDEN RING"
            )
        )
    }
}

#Preview("搜索路径 / Search path") {
    NavigationStack {
        GameDetailView(
            route: GameDetailRoute(
                appID: 1245620,
                uuid: nil,
                title: "ELDEN RING"
            )
        )
    }
}
