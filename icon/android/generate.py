"""生成 Android 自适应图标的三层 SVG（foreground / background / monochrome）。

几何数据抄自 icon/layers/*.svg（1024 画布），加上 icon.json 里蒸汽上移的 25pt；
输出画布是 108×108（单位 = dp），图形整体缩放到中间直径 60dp 的圆里（官方安全区是 66dp）。
输出只用 path / 线性渐变 / evenodd 挖洞，不用 mask 和 transform，方便转成 VectorDrawable。

用法：python3 icon/android/generate.py
"""

import math
from pathlib import Path

OUTPUT_DIR = Path(__file__).parent

CANVAS_DP = 108
CENTER_DP = CANVAS_DP / 2
# 图形外接圆的半径。官方上限是 33（图形不超过中间 66×66dp）；
# 用 30 是和 iOS 图标里图形占的比例一样（外接圆约占可见宽度的 82%）。
SAFE_RADIUS_DP = 30

BACKGROUND_TOP = "#2A3F5A"
BACKGROUND_BOTTOM = "#121C28"
# icon.json 的渐变终点在 70% 高度。
BACKGROUND_STOP = 0.7
STEAM_COLOR = "#FFFFFF"
STEAM_OPACITY = 0.9
TAG_COLOR = "#8DCF4A"
PERCENT_COLOR = "#14301C"
MONOCHROME_COLOR = "#FFFFFF"

# --- 1024 画布上的原始几何（同 icon/layers/） ---

STEAM_OFFSET_Y = -25
STEAM_WIDTH = 44
STEAM_CURVES = [
    [(400, 500), (450, 450), (350, 400), (400, 340), (450, 280), (350, 240), (400, 190)],
    [(512, 480), (562, 420), (462, 370), (512, 300), (562, 240), (462, 200), (512, 140)],
    [(624, 500), (674, 450), (574, 400), (624, 340), (674, 280), (574, 240), (624, 190)],
]

TAG_ORIGIN = (512, 700)
TAG_ROTATION_DEG = -10
TAG_SCALE = 1.12
TAG_POLYGON = [(-270, 0), (-150, -110), (250, -110), (250, 110), (-150, 110)]
TAG_STROKE_WIDTH = 60
TAG_HOLE_CENTER = (-170, 0)
TAG_HOLE_RADIUS = 30

PERCENT_ORIGIN = (560, 700)
PERCENT_ROTATION_DEG = -10
PERCENT_WIDTH = 26
PERCENT_LINE = [(60, -70), (-60, 70)]
PERCENT_RINGS = [(-48, -44), (48, 44)]
PERCENT_RING_RADIUS = 26


def rotate_then_move(point, origin, rotation_deg, scale=1.0):
    """按 SVG 的 translate(origin) rotate(rotation) scale(scale) 把局部坐标换成画布坐标。"""
    angle = math.radians(rotation_deg)
    x = point[0] * scale
    y = point[1] * scale
    rotated_x = x * math.cos(angle) - y * math.sin(angle)
    rotated_y = x * math.sin(angle) + y * math.cos(angle)
    return (origin[0] + rotated_x, origin[1] + rotated_y)


def steam_curves_on_canvas():
    """三缕蒸汽，已上移 25。"""
    curves = []
    for curve in STEAM_CURVES:
        moved = [(x, y + STEAM_OFFSET_Y) for (x, y) in curve]
        curves.append(moved)
    return curves


def tag_polygon_on_canvas():
    return [rotate_then_move(point, TAG_ORIGIN, TAG_ROTATION_DEG, TAG_SCALE) for point in TAG_POLYGON]


def tag_hole_on_canvas():
    center = rotate_then_move(TAG_HOLE_CENTER, TAG_ORIGIN, TAG_ROTATION_DEG, TAG_SCALE)
    radius = TAG_HOLE_RADIUS * TAG_SCALE
    return center, radius


def percent_on_canvas():
    line = [rotate_then_move(point, PERCENT_ORIGIN, PERCENT_ROTATION_DEG) for point in PERCENT_LINE]
    rings = [rotate_then_move(point, PERCENT_ORIGIN, PERCENT_ROTATION_DEG) for point in PERCENT_RINGS]
    return line, rings


# --- 缩放到 108dp 画布 ---

def cubic_point(p0, p1, p2, p3, t):
    u = 1 - t
    x = u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0]
    y = u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1]
    return (x, y)


def outline_samples():
    """图形边缘上的采样点，每个点带「往外还要加多少」（描边的一半）。"""
    samples = []
    for curve in steam_curves_on_canvas():
        segments = [curve[0:4], curve[3:7]]
        for segment in segments:
            for step in range(41):
                point = cubic_point(*segment, step / 40)
                samples.append((point, STEAM_WIDTH / 2))
    tag_pad = TAG_STROKE_WIDTH * TAG_SCALE / 2
    for point in tag_polygon_on_canvas():
        samples.append((point, tag_pad))
    return samples


def enclosing_circle():
    """包住整个图形的最小圆（网格搜索圆心，精度 1 个画布单位）。"""
    samples = outline_samples()
    xs = [point[0] for (point, _) in samples]
    ys = [point[1] for (point, _) in samples]
    best_center = None
    best_radius = math.inf
    for cx in range(int(min(xs)), int(max(xs)) + 1):
        for cy in range(int(min(ys)), int(max(ys)) + 1):
            distances = [math.dist(point, (cx, cy)) + pad for (point, pad) in samples]
            radius = max(distances)
            if radius < best_radius:
                best_radius = radius
                best_center = (cx, cy)
    return best_center, best_radius


class Fit:
    """1024 画布 → 108dp 画布：图形的最小外接圆对齐到中心、半径 = radius_dp。"""

    def __init__(self, radius_dp=SAFE_RADIUS_DP):
        center, radius = enclosing_circle()
        self.center = center
        self.scale = radius_dp / radius

    def point(self, point):
        x = (point[0] - self.center[0]) * self.scale + CENTER_DP
        y = (point[1] - self.center[1]) * self.scale + CENTER_DP
        return (x, y)

    def length(self, value):
        return value * self.scale


# --- 生成 path data ---

def fmt(value):
    text = f"{value:.3f}".rstrip("0").rstrip(".")
    if text == "-0":
        return "0"
    return text


def pt(point):
    return f"{fmt(point[0])},{fmt(point[1])}"


def circle_path(center, radius):
    left = (center[0] - radius, center[1])
    right = (center[0] + radius, center[1])
    r = fmt(radius)
    return f"M{pt(right)} A{r},{r} 0 1 0 {pt(left)} A{r},{r} 0 1 0 {pt(right)} Z"


def polygon_path(points):
    first = points[0]
    rest = " ".join(f"L{pt(point)}" for point in points[1:])
    return f"M{pt(first)} {rest} Z"


def capsule_path(start, end, half_width):
    """一条圆头粗线的轮廓（给单色层挖 % 号的斜杠用）。"""
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    length = math.hypot(dx, dy)
    nx = -dy / length * half_width
    ny = dx / length * half_width
    a1 = (start[0] + nx, start[1] + ny)
    b1 = (end[0] + nx, end[1] + ny)
    b2 = (end[0] - nx, end[1] - ny)
    a2 = (start[0] - nx, start[1] - ny)
    r = fmt(half_width)
    return f"M{pt(a1)} L{pt(b1)} A{r},{r} 0 0 0 {pt(b2)} L{pt(a2)} A{r},{r} 0 0 0 {pt(a1)} Z"


def steam_paths(fit):
    paths = []
    for curve in steam_curves_on_canvas():
        points = [fit.point(point) for point in curve]
        data = f"M{pt(points[0])} C{pt(points[1])} {pt(points[2])} {pt(points[3])} C{pt(points[4])} {pt(points[5])} {pt(points[6])}"
        paths.append(data)
    return paths


def tag_outline(fit):
    return [fit.point(point) for point in tag_polygon_on_canvas()]


def tag_hole(fit):
    center, radius = tag_hole_on_canvas()
    return fit.point(center), fit.length(radius)


def percent_shapes(fit):
    line, rings = percent_on_canvas()
    line_points = [fit.point(point) for point in line]
    ring_centers = [fit.point(point) for point in rings]
    return line_points, ring_centers


# --- 三个 SVG ---

def svg_document(body):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {CANVAS_DP} {CANVAS_DP}" '
        f'width="{CANVAS_DP}" height="{CANVAS_DP}">\n{body}</svg>\n'
    )


def background_body():
    stop_y = fmt(CANVAS_DP * BACKGROUND_STOP)
    return (
        '  <defs>\n'
        f'    <linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="{stop_y}">\n'
        f'      <stop offset="0" stop-color="{BACKGROUND_TOP}"/>\n'
        f'      <stop offset="1" stop-color="{BACKGROUND_BOTTOM}"/>\n'
        '    </linearGradient>\n'
        '  </defs>\n'
        f'  <path fill="url(#bg)" d="M0,0 L{CANVAS_DP},0 L{CANVAS_DP},{CANVAS_DP} L0,{CANVAS_DP} Z"/>\n'
    )


def foreground_body(fit):
    steam_width = fmt(fit.length(STEAM_WIDTH))
    tag_width = fmt(fit.length(TAG_STROKE_WIDTH * TAG_SCALE))
    percent_width = fmt(fit.length(PERCENT_WIDTH))
    ring_radius = fit.length(PERCENT_RING_RADIUS)
    outline = tag_outline(fit)
    hole_center, hole_radius = tag_hole(fit)
    line_points, ring_centers = percent_shapes(fit)

    lines = []
    for data in steam_paths(fit):
        lines.append(
            f'  <path fill="none" stroke="{STEAM_COLOR}" stroke-opacity="{STEAM_OPACITY}" '
            f'stroke-width="{steam_width}" stroke-linecap="round" d="{data}"/>'
        )
    # 标签：填充带绳孔（evenodd），描边单独一条 path（描边只画外轮廓，不画孔）。
    tag_fill = f"{polygon_path(outline)} {circle_path(hole_center, hole_radius)}"
    lines.append(f'  <path fill="{TAG_COLOR}" fill-rule="evenodd" d="{tag_fill}"/>')
    lines.append(
        f'  <path fill="none" stroke="{TAG_COLOR}" stroke-width="{tag_width}" '
        f'stroke-linejoin="round" d="{polygon_path(outline)}"/>'
    )
    percent_data = f"M{pt(line_points[0])} L{pt(line_points[1])}"
    for center in ring_centers:
        percent_data += " " + circle_path(center, ring_radius)
    lines.append(
        f'  <path fill="none" stroke="{PERCENT_COLOR}" stroke-width="{percent_width}" '
        f'stroke-linecap="round" d="{percent_data}"/>'
    )
    return "\n".join(lines) + "\n"


def monochrome_body(fit):
    """单色层：系统只看形状、自己上色，所以 % 号要从标签里挖掉才看得出来。"""
    steam_width = fmt(fit.length(STEAM_WIDTH))
    tag_width = fmt(fit.length(TAG_STROKE_WIDTH * TAG_SCALE))
    half_percent = fit.length(PERCENT_WIDTH) / 2
    ring_radius = fit.length(PERCENT_RING_RADIUS)
    outline = tag_outline(fit)
    hole_center, hole_radius = tag_hole(fit)
    line_points, ring_centers = percent_shapes(fit)

    lines = []
    for data in steam_paths(fit):
        lines.append(
            f'  <path fill="none" stroke="{MONOCHROME_COLOR}" stroke-width="{steam_width}" '
            f'stroke-linecap="round" d="{data}"/>'
        )
    # evenodd：标签轮廓 − 绳孔 − 斜杠 − 圆环（外圆挖掉、内圆补回来）。
    cutouts = [circle_path(hole_center, hole_radius), capsule_path(line_points[0], line_points[1], half_percent)]
    for center in ring_centers:
        cutouts.append(circle_path(center, ring_radius + half_percent))
        cutouts.append(circle_path(center, ring_radius - half_percent))
    tag_fill = polygon_path(outline) + " " + " ".join(cutouts)
    lines.append(f'  <path fill="{MONOCHROME_COLOR}" fill-rule="evenodd" d="{tag_fill}"/>')
    lines.append(
        f'  <path fill="none" stroke="{MONOCHROME_COLOR}" stroke-width="{tag_width}" '
        f'stroke-linejoin="round" d="{polygon_path(outline)}"/>'
    )
    return "\n".join(lines) + "\n"


def main():
    fit = Fit()
    outputs = {
        "background.svg": background_body(),
        "foreground.svg": foreground_body(fit),
        "monochrome.svg": monochrome_body(fit),
    }
    for name, body in outputs.items():
        path = OUTPUT_DIR / name
        path.write_text(svg_document(body))
        print(f"wrote {path}")
    print(f"center on 1024 canvas: {fit.center}, scale: {fit.scale:.5f} dp per unit")


if __name__ == "__main__":
    main()
