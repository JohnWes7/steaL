"""生成 Android 图标：自适应图标的三层（background / foreground / monochrome）、Android 7.x 的位图。
输出 icon/android/ 下的 SVG（看效果用）、androidApp 的 VectorDrawable（res/drawable/ic_launcher_*.xml）
和 res/mipmap-*dpi/ic_launcher.png（用本机 Chrome 无界面模式渲染，要装 Google Chrome）。

几何数据抄自 icon/layers/*.svg（1024 画布），加上 icon.json 里蒸汽上移的 25pt；
输出画布是 108×108（单位 = dp），图形整体缩放到中间直径 60dp 的圆里（官方安全区是 66dp）。
只用 path / 线性渐变 / evenodd 挖洞，不用 mask 和 transform：VectorDrawable 不支持 mask。

用法：python3 icon/android/generate.py
"""

import math
import subprocess
import tempfile
from pathlib import Path

OUTPUT_DIR = Path(__file__).resolve().parent
REPO_ROOT = OUTPUT_DIR.parent.parent
DRAWABLE_DIR = REPO_ROOT / "androidApp/src/main/res/drawable"
RES_DIR = REPO_ROOT / "androidApp/src/main/res"
CHROME = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")

# Android 8.0 以下没有自适应图标，用各密度的位图：48dp 在各密度下的像素。
LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
# 位图的画布 48，圆角方 44（四周留 2）、圆角半径 11；里面放自适应图标中间可见的 72dp。
LEGACY_CANVAS = 48
LEGACY_INSET = 2
LEGACY_CORNER = 11
VISIBLE_DP = 72

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


# --- 三层的图形：每层是一列 Shape，再分别写成 SVG 和 VectorDrawable ---

class Shape:
    """一条 path：填充或描边（两者只用其一）。"""

    def __init__(self, data, fill=None, even_odd=False, stroke=None, stroke_width=None,
                 stroke_opacity=1.0, round_cap=False, round_join=False):
        self.data = data
        self.fill = fill
        self.even_odd = even_odd
        self.stroke = stroke
        self.stroke_width = stroke_width
        self.stroke_opacity = stroke_opacity
        self.round_cap = round_cap
        self.round_join = round_join


def foreground_shapes(fit):
    steam_width = fit.length(STEAM_WIDTH)
    tag_width = fit.length(TAG_STROKE_WIDTH * TAG_SCALE)
    percent_width = fit.length(PERCENT_WIDTH)
    ring_radius = fit.length(PERCENT_RING_RADIUS)
    outline = tag_outline(fit)
    hole_center, hole_radius = tag_hole(fit)
    line_points, ring_centers = percent_shapes(fit)

    shapes = []
    for data in steam_paths(fit):
        shapes.append(Shape(data, stroke=STEAM_COLOR, stroke_width=steam_width,
                            stroke_opacity=STEAM_OPACITY, round_cap=True))
    # 标签：填充带绳孔（evenodd），描边单独一条 path（描边只画外轮廓，不画孔）。
    tag_fill = f"{polygon_path(outline)} {circle_path(hole_center, hole_radius)}"
    shapes.append(Shape(tag_fill, fill=TAG_COLOR, even_odd=True))
    shapes.append(Shape(polygon_path(outline), stroke=TAG_COLOR, stroke_width=tag_width, round_join=True))
    percent_data = f"M{pt(line_points[0])} L{pt(line_points[1])}"
    for center in ring_centers:
        percent_data += " " + circle_path(center, ring_radius)
    shapes.append(Shape(percent_data, stroke=PERCENT_COLOR, stroke_width=percent_width, round_cap=True))
    return shapes


def monochrome_shapes(fit):
    """单色层：系统只看形状、自己上色，所以 % 号要从标签里挖掉才看得出来。"""
    steam_width = fit.length(STEAM_WIDTH)
    tag_width = fit.length(TAG_STROKE_WIDTH * TAG_SCALE)
    half_percent = fit.length(PERCENT_WIDTH) / 2
    ring_radius = fit.length(PERCENT_RING_RADIUS)
    outline = tag_outline(fit)
    hole_center, hole_radius = tag_hole(fit)
    line_points, ring_centers = percent_shapes(fit)

    shapes = []
    for data in steam_paths(fit):
        shapes.append(Shape(data, stroke=MONOCHROME_COLOR, stroke_width=steam_width, round_cap=True))
    # evenodd：标签轮廓 − 绳孔 − 斜杠 − 圆环（外圆挖掉、内圆补回来）。
    cutouts = [circle_path(hole_center, hole_radius), capsule_path(line_points[0], line_points[1], half_percent)]
    for center in ring_centers:
        cutouts.append(circle_path(center, ring_radius + half_percent))
        cutouts.append(circle_path(center, ring_radius - half_percent))
    tag_fill = polygon_path(outline) + " " + " ".join(cutouts)
    shapes.append(Shape(tag_fill, fill=MONOCHROME_COLOR, even_odd=True))
    shapes.append(Shape(polygon_path(outline), stroke=MONOCHROME_COLOR, stroke_width=tag_width, round_join=True))
    return shapes


BACKGROUND_PATH = f"M0,0 L{CANVAS_DP},0 L{CANVAS_DP},{CANVAS_DP} L0,{CANVAS_DP} Z"


# --- SVG ---

def svg_shape(shape):
    attributes = []
    if shape.fill:
        attributes.append(f'fill="{shape.fill}"')
        if shape.even_odd:
            attributes.append('fill-rule="evenodd"')
    else:
        attributes.append('fill="none"')
    if shape.stroke:
        attributes.append(f'stroke="{shape.stroke}"')
        if shape.stroke_opacity != 1.0:
            attributes.append(f'stroke-opacity="{fmt(shape.stroke_opacity)}"')
        attributes.append(f'stroke-width="{fmt(shape.stroke_width)}"')
        if shape.round_cap:
            attributes.append('stroke-linecap="round"')
        if shape.round_join:
            attributes.append('stroke-linejoin="round"')
    attributes.append(f'd="{shape.data}"')
    return "  <path " + " ".join(attributes) + "/>"


def svg_document(body):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {CANVAS_DP} {CANVAS_DP}" '
        f'width="{CANVAS_DP}" height="{CANVAS_DP}">\n{body}</svg>\n'
    )


def svg_shapes(shapes):
    lines = [svg_shape(shape) for shape in shapes]
    return svg_document("\n".join(lines) + "\n")


def svg_background():
    stop_y = fmt(CANVAS_DP * BACKGROUND_STOP)
    body = (
        '  <defs>\n'
        f'    <linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="{stop_y}">\n'
        f'      <stop offset="0" stop-color="{BACKGROUND_TOP}"/>\n'
        f'      <stop offset="1" stop-color="{BACKGROUND_BOTTOM}"/>\n'
        '    </linearGradient>\n'
        '  </defs>\n'
        f'  <path fill="url(#bg)" d="{BACKGROUND_PATH}"/>\n'
    )
    return svg_document(body)


# --- VectorDrawable ---

GENERATED_NOTE = "<!-- 由 icon/android/generate.py 生成，不要手改 / Generated by icon/android/generate.py; do not edit -->"


def vector_shape(shape):
    attributes = [f'android:pathData="{shape.data}"']
    if shape.fill:
        attributes.append(f'android:fillColor="{shape.fill}"')
        if shape.even_odd:
            attributes.append('android:fillType="evenOdd"')
    if shape.stroke:
        attributes.append(f'android:strokeColor="{shape.stroke}"')
        if shape.stroke_opacity != 1.0:
            attributes.append(f'android:strokeAlpha="{fmt(shape.stroke_opacity)}"')
        attributes.append(f'android:strokeWidth="{fmt(shape.stroke_width)}"')
        if shape.round_cap:
            attributes.append('android:strokeLineCap="round"')
        if shape.round_join:
            attributes.append('android:strokeLineJoin="round"')
    joined = "\n        ".join(attributes)
    return f"    <path\n        {joined} />"


def vector_document(body, uses_aapt=False):
    aapt = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if uses_aapt else ""
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'{GENERATED_NOTE}\n'
        f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{aapt}\n'
        f'    android:width="{CANVAS_DP}dp"\n'
        f'    android:height="{CANVAS_DP}dp"\n'
        f'    android:viewportWidth="{CANVAS_DP}"\n'
        f'    android:viewportHeight="{CANVAS_DP}">\n'
        f'{body}'
        '</vector>\n'
    )


def vector_shapes(shapes):
    lines = [vector_shape(shape) for shape in shapes]
    return vector_document("\n".join(lines) + "\n")


def vector_background():
    stop_y = fmt(CANVAS_DP * BACKGROUND_STOP)
    body = (
        f'    <path android:pathData="{BACKGROUND_PATH}">\n'
        '        <aapt:attr name="android:fillColor">\n'
        '            <gradient\n'
        '                android:type="linear"\n'
        '                android:startX="0"\n'
        '                android:startY="0"\n'
        '                android:endX="0"\n'
        f'                android:endY="{stop_y}">\n'
        f'                <item android:offset="0" android:color="{BACKGROUND_TOP}" />\n'
        f'                <item android:offset="1" android:color="{BACKGROUND_BOTTOM}" />\n'
        '            </gradient>\n'
        '        </aapt:attr>\n'
        '    </path>\n'
    )
    return vector_document(body, uses_aapt=True)


# --- Android 7.x 的位图 ---

def legacy_svg(foreground, pixel_size):
    """背景 + 前景裁成圆角方，画布 48、输出 pixel_size 像素。"""
    side = LEGACY_CANVAS - LEGACY_INSET * 2
    scale = side / VISIBLE_DP
    hidden = (CANVAS_DP - VISIBLE_DP) / 2
    stop_y = fmt(CANVAS_DP * BACKGROUND_STOP)
    shapes = "\n".join(svg_shape(shape) for shape in foreground)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {LEGACY_CANVAS} {LEGACY_CANVAS}" '
        f'width="{pixel_size}" height="{pixel_size}">\n'
        '  <defs>\n'
        f'    <clipPath id="shape"><rect x="{LEGACY_INSET}" y="{LEGACY_INSET}" width="{side}" height="{side}" rx="{LEGACY_CORNER}"/></clipPath>\n'
        f'    <linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="{stop_y}">\n'
        f'      <stop offset="0" stop-color="{BACKGROUND_TOP}"/>\n'
        f'      <stop offset="1" stop-color="{BACKGROUND_BOTTOM}"/>\n'
        '    </linearGradient>\n'
        '  </defs>\n'
        '  <g clip-path="url(#shape)">\n'
        f'    <g transform="translate({LEGACY_INSET} {LEGACY_INSET}) scale({fmt(scale)}) translate({fmt(-hidden)} {fmt(-hidden)})">\n'
        f'  <path fill="url(#bg)" d="{BACKGROUND_PATH}"/>\n'
        f'{shapes}\n'
        '    </g>\n'
        '  </g>\n'
        '</svg>\n'
    )


def render_png(svg_text, pixel_size, output):
    """用 Chrome 无界面截图把 SVG 画成透明底的 PNG（qlmanage 会把透明处填白）。"""
    with tempfile.TemporaryDirectory() as folder:
        svg_path = Path(folder) / "icon.svg"
        svg_path.write_text(svg_text)
        command = [
            str(CHROME), "--headless", "--disable-gpu", "--hide-scrollbars",
            "--default-background-color=00000000",
            f"--window-size={pixel_size},{pixel_size}",
            f"--screenshot={output}",
            svg_path.as_uri(),
        ]
        subprocess.run(command, check=True, capture_output=True)


def legacy_outputs(foreground):
    for density, pixel_size in LEGACY_SIZES.items():
        output = RES_DIR / f"mipmap-{density}" / "ic_launcher.png"
        output.parent.mkdir(exist_ok=True)
        render_png(legacy_svg(foreground, pixel_size), pixel_size, output)
        print(f"wrote {output.relative_to(REPO_ROOT)}")


def main():
    fit = Fit()
    foreground = foreground_shapes(fit)
    monochrome = monochrome_shapes(fit)
    outputs = {
        OUTPUT_DIR / "background.svg": svg_background(),
        OUTPUT_DIR / "foreground.svg": svg_shapes(foreground),
        OUTPUT_DIR / "monochrome.svg": svg_shapes(monochrome),
        DRAWABLE_DIR / "ic_launcher_background.xml": vector_background(),
        DRAWABLE_DIR / "ic_launcher_foreground.xml": vector_shapes(foreground),
        DRAWABLE_DIR / "ic_launcher_monochrome.xml": vector_shapes(monochrome),
        OUTPUT_DIR / "legacy.svg": legacy_svg(foreground, 192),
    }
    for path, text in outputs.items():
        path.write_text(text)
        print(f"wrote {path.relative_to(REPO_ROOT)}")
    legacy_outputs(foreground)
    print(f"center on 1024 canvas: {fit.center}, scale: {fit.scale:.5f} dp per unit")


if __name__ == "__main__":
    main()
