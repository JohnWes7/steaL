//
//  PriceTagShape.swift
//  steaL
//
//  Created by yijiang on 2026/9/23.
//

import SwiftUI

/// 价签轮廓：左端尖角 + 绳孔，呼应 App 图标的标签；绳孔要配 `FillStyle(eoFill: true)` 才是空的 /
/// Price-tag outline with a pointed leading end and a string hole, echoing the app icon; the hole only cuts through with `FillStyle(eoFill: true)`
struct PriceTagShape: Shape {

    /// 尖角深度占高度的比例，比图标浅，免得裁掉太多封面 / Point depth as a fraction of the height; shallower than the icon so less artwork is lost
    var pointDepth: CGFloat = 0.34

    /// 四个直角与两个肩角的圆角 / Radius for the square and shoulder corners
    var cornerRadius: CGFloat = 8

    /// 绳孔半径占高度的比例 / Hole radius as a fraction of the height
    var holeRadius: CGFloat = 0.07

    func path(in rect: CGRect) -> Path {
        let depth = rect.height * pointDepth
        let topEdgeMid = CGPoint(x: rect.midX, y: rect.minY)
        let topRight = CGPoint(x: rect.maxX, y: rect.minY)
        let bottomRight = CGPoint(x: rect.maxX, y: rect.maxY)
        let bottomShoulder = CGPoint(x: rect.minX + depth, y: rect.maxY)
        let tip = CGPoint(x: rect.minX, y: rect.midY)
        let topShoulder = CGPoint(x: rect.minX + depth, y: rect.minY)

        var path = Path()
        // 从上边中点起笔，每个角用切线圆弧倒圆，和图标 SVG 的圆头描边效果一致 /
        // Start mid-top and round every corner with a tangent arc, matching the icon SVG's round-joined stroke
        path.move(to: topEdgeMid)
        path.addArc(tangent1End: topRight, tangent2End: bottomRight, radius: cornerRadius)
        path.addArc(tangent1End: bottomRight, tangent2End: bottomShoulder, radius: cornerRadius)
        path.addArc(tangent1End: bottomShoulder, tangent2End: tip, radius: cornerRadius)
        path.addArc(tangent1End: tip, tangent2End: topShoulder, radius: cornerRadius * 0.6)
        path.addArc(tangent1End: topShoulder, tangent2End: topEdgeMid, radius: cornerRadius)
        path.closeSubpath()

        // 绳孔在肩线左侧一点，位置比例取自图标 / The hole sits just left of the shoulder line, proportioned after the icon
        let radius = rect.height * holeRadius
        let center = CGPoint(x: rect.minX + depth * 0.85, y: rect.midY)
        path.addEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
        return path
    }
}

#Preview {
    PriceTagShape()
        .fill(Color.brandGreen, style: FillStyle(eoFill: true))
        .frame(width: 128, height: 60)
        .padding()
        .background(Color.appBackground)
}
