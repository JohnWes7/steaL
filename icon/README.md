# App Icon 概念稿

三套 1024×1024 的 SVG 概念稿，颜色取自 Asset Catalog 的品牌色
（`BrandGreen` #248A3D / #8DCF4A，`BrandBlue` #3D84FF）。

| 目录 | 概念 | 背景（在 Icon Composer 里设） | 图层（从下到上） |
| --- | --- | --- | --- |
| `A-price-tag/` | 价格标签 + 下箭头 | 渐变 #8DCF4A → #248A3D | `1-tag.svg`、`2-arrow.svg` |
| `B-L-chart/` | steaL 的 L = 下跌到最低价的折线 | 渐变 #5A98FF → #1F55C8 | `1-line.svg`、`2-dot.svg` |
| `C-steam-tag/` | 蒸汽 + 折扣标签 | 渐变 #2A3F5A → #121C28 | `1-steam.svg`、`2-tag-glass.svg`、`3-percent-glass.svg` |

- `preview.svg`：带背景和圆角的合成图，只用来看效果，**不要**拖进 Icon Composer。
- 编号图层：透明底、方形画布，直接拖进 Icon Composer，每个文件一层。

## C 的蒸汽备选

用户已选定 C。`C-steam-tag/steam-options/` 里放了蒸汽层的备选，每个都有 `steam-*.svg`（透明底图层）
和 `preview-*.svg`（带背景和标签的合成图）：

| 文件 | 样子 | 小尺寸表现 |
| --- | --- | --- |
| `1-steam.svg`（当前，A 原版） | 三缕等粗的 S 形卷曲 | 好 |
| `steam-B-puff.svg` | 云朵状白雾 + 两个飘走的小团 | 好，但更像云 |
| `steam-C-taper.svg` | 上细下粗，每缕三段变化 | 差，60pt 下糊成一团 |
| `steam-D-dashed.svg` | 断续虚线的卷曲 | 差，像一排胶囊 |
| `steam-E-soft.svg` | 每缕只弯一次，高矮粗细不同 | 最好 |
| `steam-F-bubbles.svg` | 上升的气泡，越上越小越淡 | 差，小尺寸几乎看不见 |

换一种：`cp C-steam-tag/steam-options/steam-E-soft.svg C-steam-tag/1-steam.svg`，
或者在 Icon Composer 里直接拖对应的 `steam-*.svg` 当蒸汽层。

## C 的 % 号：两层独立玻璃叠放（现行方案）

之前试过的「挖孔 + 宝石嵌入」（`2-tag-cutout.svg` 挖空 + `3-percent-gem.svg` 嵌进洞里，
两者共用一个 Liquid Glass 组）实测很丑：宝石嵌在标签表面的凹槽里，看起来像补丁而不是独立的物体。

参考 Apple 自家图标（Home 的嵌套房子、Passwords 的三把钥匙）：它们都是**互相独立的实心玻璃图层直接
堆叠**，靠各自的阴影/ 高光分出前后层次，而不是把一层刻进另一层里。现在照这个思路重做：

| 图层 | 文件 | 说明 |
| --- | --- | --- |
| 标签（后） | `2-tag-glass.svg` | 和旧版一样的标签外形，只留绳孔；纯白色，颜色由 `icon.json` 的 `fill` 上色 |
| % 号（前） | `3-percent-glass.svg` | 两个实心圆环 + 一条圆头斜杠，**填色**而不是描边，独立一个 Liquid Glass 组 |

两者是 `icon.json` 里**两个平级的 group**（不再共用一组），`groups` 数组顺序 `3-percent-glass` → `2-tag-glass`
→ `1-steam`（前到后），% 号叠在标签正面、自己投一层阴影，视觉上是两片玻璃摞在一起，不是挖孔嵌入。
旧的 `2-tag.svg` / `2-tag-cutout.svg` / `3-percent.svg` / `3-percent-gem.svg` 保留在目录里作为历史参考，
`Icon-steam-tag.icon/Assets/` 里已经不再引用它们。

## 命令行渲染（不用开 Icon Composer）

`icon-composer-mcp` 这个 CLI 调 Apple 的 `ictool` 出图，改完 SVG / icon.json 立刻能看结果：

```sh
npm install -g icon-composer-mcp          # 依赖 Xcode 自带的 ictool
icon-composer doctor                      # 检查环境
icon-composer render icon/C-steam-tag/Icon-steam-tag.icon out.png
icon-composer render ... out-dark.png --rendition Dark      # 深色外观
icon-composer render ... out-small.png --width 180 --height 180
icon-composer inspect icon/C-steam-tag/Icon-steam-tag.icon  # 看当前参数
```

项目根目录的 `.mcp.json` 把它也注册成了 MCP server（已 gitignore）。

### App 内显示的图标副本（`AppIconImage`）

登录 sheet 顶部显示的是图标的静态 PNG：`steaL/Assets.xcassets/AppIconImage.imageset/AppIconImage.png`
（代码里是 `Image(.appIcon)`，Xcode 生成符号时去掉了名字末尾的 "Image"）。App Icon 本身在代码里读不到，
Liquid Glass 也只有系统能实时渲染，所以只能放一张快照。`render` 出来的图已经带圆角遮罩和透明角，不用再裁。
只用默认外观一张，不做深色版。**改了 `.icon` 之后要重新导出**，否则 App 里和主屏幕上的图标会不一样：

```sh
icon-composer render icon/C-steam-tag/Icon-steam-tag.icon /tmp/icon.png
sips -Z 192 /tmp/icon.png --out steaL/Assets.xcassets/AppIconImage.imageset/AppIconImage.png   # 64pt @3x
```

## icon.json 的完整 schema

`giginet/apple-icon-composer-skill` 这个 marketplace clone 到本地后，schema 在：

```
~/.claude/plugins/marketplaces/icon-composer/plugins/icon-composer/skills/compose-app-icon/scripts/icon-schema.json
```

Group 可用 key：`lighting`（individual / combined）、`specular`、`blur`、`shadow`、`translucency`、
`blend-mode`、`opacity`、`hidden`、`position`。
Layer 可用 key：`image-name`、`fill`、`blend-mode`、`opacity`、`glass`、`hidden`、`position`。

**Icon Composer 2.0+ 才有的、本机 1.6 用不了的**：`refractivity`（折射）、`blur-material`、
`specular-highlight-placement`。前两者实测被 1.6 的 `ictool` 直接忽略（渲染结果字节级相同）；
而且嵌套写法要求在文档顶层的 `features` 里声明 `refractivity`，1.6 遇到不认识的 feature 会
拒绝打开整个文档，所以不要写。本机 Icon Composer 版本：
`defaults read "/Applications/Xcode.app/Contents/Applications/Icon Composer.app/Contents/Info.plist" CFBundleShortVersionString`

## Icon Composer 踩坑

- **Liquid Glass → Effects 默认是关的**，不开就没有任何光泽，Fill 调什么都没用。
- **Blend Mode 必须是 Normal**。Multiply 会把这层和下面的颜色相乘，结果只会更暗，高光直接没了。
- **Fill 不能留 Automatic**。Automatic = 用 SVG 自带颜色，不会被当成可染色的玻璃材质；要手动指定颜色或渐变。
- **Shadow / Specular / Translucency / Lighting 在 Group 这一级**（`icon.json` 里这几个 key 挂在 group 上），
  图层这一级只有 Opacity / Blend Mode / Fill / Effects / Image / Layout。根节点只有背景 Fill。
- 各区块右边的 `All ⌄` 表示这个设置应用到哪个外观（All / Default / Dark / Tinted）。
- ~~一个图标里只让一个元素是玻璃~~：这条是旧结论，只在「挖孔嵌入」的做法下成立（同一块面积两层玻璃会
  互相抵消）。**两个互不重叠、各自独立的形状**（现在的标签 + % 号）都开玻璃完全没问题，效果反而更像
  Apple 自家图标的堆叠质感，参照上面「两层独立玻璃叠放」。
- 玻璃需要深色背景来衬；现在标签本身也是玻璃了，靠的是标签自己的阴影把背景衬深，% 号才在标签上显出来。
- **白色 Fill + specular + translucency 拉满 = 铬合金**，不是彩色玻璃。玻璃材质不自带颜色，
  颜色全来自 Fill；要彩色玻璃就指定饱和颜色，并把 translucency 降到 0.3～0.5
  （拉满时底下的背景整个透上来，颜色被冲淡）。
- **细节尺寸要留够余量**：玻璃会给形状加厚，% 圆环中间的洞原来只有 26pt，加厚后直接糊死。
  现在改成半径 46 / 笔画 34（洞 58pt）。
- **`.icon` 是文件包，Assets/ 里是 SVG 的副本**。改仓库里的源 SVG 不会同步进去，
  要么在 Icon Composer 的 Image 下拉里重新指一次，要么直接 `cp` 覆盖 `Icon-xxx.icon/Assets/` 下的同名文件。

## 背景怎么设（Icon Composer）

Color 选 **Default** 外观 → Fill 选 **Gradient** → 下面那格（渐变底部）填 `#121C28`，
上面那格（顶部）填 `#2A3F5A`，留着 `Auto` 让它自动推也可以。渐变方向固定从上到下。

## 放进 Icon Composer

1. Xcode → Open Developer Tool → Icon Composer，新建图标。
2. 在 Icon Composer 里把背景（Fill）设成上表的渐变；图层按编号从下到上拖进去。
   **左边面板越靠上 = 越靠前**（Group 之间同理），所以 C 的正确顺序自上而下是
   `3-percent-glass` → `2-tag-glass` → `1-steam`。三个都各自单独一组（不共用玻璃/阴影），
   这样 % 号才能作为独立于标签的一片玻璃叠在上面。
3. 分别调整 Default / Dark / Mono 外观（玻璃、阴影、深色配色由它生成）。
4. 保存为 `AppIcon.icon`，拖进 Xcode 的 `steaL` 目录；删掉 `Assets.xcassets/AppIcon.appiconset`，
   确认 target 的 App Icon 设置仍是 `AppIcon`。
