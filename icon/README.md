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

## C 的 % 号：哑光版 vs 宝石版

两套互斥的做法，选一套：

| 做法 | 图层 | 说明 |
| --- | --- | --- |
| 哑光（原版） | `2-tag.svg` + `3-percent.svg` | % 是刻在标签上的深绿笔画 |
| 加厚哑光 | `2-tag.svg` + `steam-options/percent-bold-{dark,white}.svg` | 同上，但笔画从 26pt 加到 42–50pt |
| **宝石（A 方案）** | `2-tag-cutout.svg` + `3-percent-gem.svg` | 标签上把 % 挖空透出深色背景，% 由单独一层实心玻璃补上 |

宝石版的洞比宝石每边宽 7pt，留一圈深色缝，玻璃的边缘高光才衬得出来。
颜色对比见 `steam-options/preview-cutout-{cyan,white,green}.svg`。

## Icon Composer 踩坑

- **Liquid Glass → Effects 默认是关的**，不开就没有任何光泽，Fill 调什么都没用。
- **Blend Mode 必须是 Normal**。Multiply 会把这层和下面的颜色相乘，结果只会更暗，高光直接没了。
- **Fill 不能留 Automatic**。Automatic = 用 SVG 自带颜色，不会被当成可染色的玻璃材质；要手动指定颜色或渐变。
- **Shadow / Specular / Translucency / Lighting 在 Group 这一级**（`icon.json` 里这几个 key 挂在 group 上），
  图层这一级只有 Opacity / Blend Mode / Fill / Effects / Image / Layout。根节点只有背景 Fill。
- 各区块右边的 `All ⌄` 表示这个设置应用到哪个外观（All / Default / Dark / Tinted）。
- **一个图标里只让一个元素是玻璃**。宝石版里标签那层的 Effects 要关掉，否则两层互相抵消，宝石不突出。
- 玻璃需要深色背景来衬。浅绿标签上直接放玻璃 % 效果很弱，这就是要把标签挖空的原因。
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
   `3-percent` → `2-tag` → `1-steam`。Group 是「共用一套玻璃 / 阴影」的单位：
   `3-percent` 与 `2-tag` 放同一组（本来就是一个物体），`1-steam` 单独一组放在后面。
3. 分别调整 Default / Dark / Mono 外观（玻璃、阴影、深色配色由它生成）。
4. 保存为 `AppIcon.icon`，拖进 Xcode 的 `steaL` 目录；删掉 `Assets.xcassets/AppIcon.appiconset`，
   确认 target 的 App Icon 设置仍是 `AppIcon`。
