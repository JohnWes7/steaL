# App Icon 概念稿

三套 1024×1024 的 SVG 概念稿，颜色取自 Asset Catalog 的品牌色
（`BrandGreen` #248A3D / #8DCF4A，`BrandBlue` #3D84FF）。

| 目录 | 概念 | 背景（在 Icon Composer 里设） | 图层（从下到上） |
| --- | --- | --- | --- |
| `A-price-tag/` | 价格标签 + 下箭头 | 渐变 #8DCF4A → #248A3D | `1-tag.svg`、`2-arrow.svg` |
| `B-L-chart/` | steaL 的 L = 下跌到最低价的折线 | 渐变 #5A98FF → #1F55C8 | `1-line.svg`、`2-dot.svg` |
| `C-steam-tag/` | 蒸汽 + 折扣标签 | 渐变 #2A3F5A → #121C28 | `1-steam.svg`、`2-tag.svg`、`3-percent.svg` |

- `preview.svg`：带背景和圆角的合成图，只用来看效果，**不要**拖进 Icon Composer。
- 编号图层：透明底、方形画布，直接拖进 Icon Composer，每个文件一层。

## 放进 Icon Composer

1. Xcode → Open Developer Tool → Icon Composer，新建图标。
2. 在 Icon Composer 里把背景（Fill）设成上表的渐变；图层按编号从下到上拖进去。
3. 分别调整 Default / Dark / Mono 外观（玻璃、阴影、深色配色由它生成）。
4. 保存为 `AppIcon.icon`，拖进 Xcode 的 `steaL` 目录；删掉 `Assets.xcassets/AppIcon.appiconset`，
   确认 target 的 App Icon 设置仍是 `AppIcon`。
