# Material Theme Builder 的配色算法（整理）

2026-11-21 为 steaL 的 Android 配色研究出来的结论。「已验证」的都和网页导出的 json 逐个比对过（`theme_builder.py verify`）。没验证的单独列在最后，用之前先验证。

## 1. 基本概念

- **HCT**：Material 的色彩空间，Hue（色相，0–360）、Chroma（彩度，越大越鲜艳）、Tone（明度，0 黑 – 100 白）。明度是按人眼感觉算的：两个颜色明度差得越多，对比度越高（用 `Contrast.ratio_of_tones` 算过：差 40 档至少 3.17:1，差 50 档至少 4.48:1，约等于 3:1 / 4.5:1 两条线）。
- **色阶（tonal palette）**：同一个色相和彩度、不同明度的一串颜色。`TonalPalette.from_int(颜色)` 保留输入色的色相和彩度；某些明度达不到那么高的彩度，算法会自动降。
- **颜色角色**：`primary`、`primaryContainer`、`surface`… 每个角色从某条色阶取某一档。Compose 的 `ColorScheme` 就是这些角色的值。
- **方案（variant）**：同样的输入，按不同规则取色阶和档位。手机壁纸取色默认是 TonalSpot（低彩度、和谐）；Theme Builder 的 Color Match 用的是 Content（保留输入色）。
- 实现：Google 的 [material-color-utilities](https://github.com/material-foundation/material-color-utilities)（Java / TS / C++ / Dart / Swift，Apache-2.0）。脚本用它的 Python 移植 [materialyoucolor](https://pypi.org/project/materialyoucolor/)（MIT，固定 3.0.4），Kotlin 里对应的是 [MaterialKolor](https://github.com/jordond/MaterialKolor)（Animeko 在用）。

## 2. Material 默认的档位（参考）

Compose material3 1.5.0 的默认配色（`ColorDarkTokens` / `ColorLightTokens`）和 TonalSpot 方案按这些档位取。Content 方案大体相同，但容器色等会按输入色调整（见第 3 节）。

| 角色 | 深色 | 浅色 | 取自 |
|---|---|---|---|
| `primary` / `onPrimary` | 80 / 20 | 40 / 100 | primary 色阶 |
| `primaryContainer` / `onPrimaryContainer` | 30 / 90 | 90 / 10 | primary 色阶 |
| secondary、tertiary、error 系 | 同上 | 同上 | 各自的色阶 |
| `surface` / `background` | 6 | 98 | neutral |
| `surfaceContainerLowest` → `Highest` | 4 / 10 / 12 / 17 / 22 | 100 / 96 / 94 / 92 / 90 | neutral |
| `surfaceDim` / `surfaceBright` | 6 / 24 | 87 / 98 | neutral |
| `onSurface` | 90 | 10 | neutral |
| `surfaceVariant` / `onSurfaceVariant` | 30 / 80 | 90 / 30 | neutral variant |
| `outline` / `outlineVariant` | 60 / 30 | 50 / 80 | neutral variant |

## 3. Color Match（已验证）

网页 README 的原话：「Color Match will use the color inputs in place of Container roles to provide color fidelity with your brand colors」。实际算法：

1. **主体**：以 Primary 为源色跑 Content 方案（`DynamicScheme(Variant.CONTENT)`）。填了的 Secondary / Tertiary / Neutral… 换成「用输入色建的色阶」（`TonalPalette.from_int`）。
2. **Secondary、Tertiary 的四个角色**（本色、on、容器、on 容器）：如果填了，就以那个输入色为源色**再单独跑一次** Content 方案，取它的 `primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer`。
3. 其余角色（表面、文字、边框、error、fixed 系）都来自第 1 步。

用 steaL 的导出（Primary #8DCF4A、Secondary #3D84FF、Tertiary #20221C）验证：6 套配色共 294 个值、5 条色阶共 90 个值全部一致。

### 由此能解释的现象

- **容器就是输入色**：深色下 `primaryContainer` = #8DCF4A，原样保留。
- **`primary` 被推亮**：规则是 `primary` 和 `primaryContainer` 明度至少差 10 档（`ToneDeltaPair(primaryContainer, primary, 10, "nearer")`）。容器在 76.5 档，所以 primary 是 86.6 档的 #A8EC63，比品牌绿亮。
- **Secondary 容器不完全等于输入色**：#3D84FF（57 档）的容器算出来是 #518DFF（60 档）。Content 方案对源色做了微调，注释里说是为了浅色、深色下看起来一致。
- **没填 Tertiary 时整页变薄荷绿**：Content 方案会自己挑一个和源色相邻的色相（绿 → 色相 163 的薄荷绿 #00D791），容器亮度和源色一样。网页示例 App 的提示条、商品卡都用 `tertiaryContainer`，所以看起来「整页都是 tertiary」。不想要就填一个颜色（steaL 填了黑色 #20221C 让它不起作用）。
- **表面带主色的色调**：Content 方案的中性色彩度约为 primary 彩度的 1/8（绿彩度 65 → 约 8），所以深色背景偏橄榄色。填 Neutral 可以换掉。

### 对比度（已验证）

`contrast_level` 0 / 0.5 / 1.0 = 网页导出的标准 / 中 / 高对比度（`light`、`light-medium-contrast`、`light-high-contrast`，深色同理）。中对比度下 `onPrimaryContainer` 会更深（steaL：#2F5600 → #1B3600）。

### 导出里的 `palettes`（已验证）

只是给人看的参考，配色本身不读它们：

- primary / secondary / tertiary：用输入色建的色阶；没填的 secondary / tertiary 取 Content 方案自己的。
- neutral / neutral-variant：没填时是 primary 的色相、彩度 4 / 8。注意这**不是**配色里表面用的中性色（那个彩度约 8，见上）。

## 4. 没验证的（用之前先验证）

- **Color Match 关掉的模式**：没有导出可比对。从网页截图取色看，`tertiaryContainer`（#1E4E4C）对得上「primary 色相 +60°、彩度 24、30 档」，但 `secondaryContainer` 对不上「输入色色阶的 30 档」。猜测是 TonalSpot 加上指定色阶，没确认，脚本也没实现。
- **填了 Neutral / Neutral Variant / Error 时网页怎么处理**：脚本按「用输入色建色阶、换掉主体方案的那一条」处理，和 Secondary 的做法一致，但没有导出可比对。steaL 的 `ThemeBuilderIosGray` 就是这样算的。
- **扩展色（Extended colors）与 harmonize**：网页会把扩展色也做成四个角色（本色、on、容器、on 容器）；harmonize 是把色相最多朝 primary 转 15°（`Blend.harmonize`）。网页 README 说「Harmonization is only to visualize with dynamic color」。脚本没实现。
- **2025 版规范**：materialyoucolor 支持 `spec_version="2025"`，在 steaL 的例子里和 2021 结果相同；脚本固定用 2021。

验证方法：在网页上用同样的设置导出 json，跑 `verify`，不一致就改 `theme_builder.py` 的 `color_match_scheme`，直到一致，再把结论补进本文件。

## 5. 实操时踩过的坑

- **网页读不到**：Theme Builder 是 Flutter 网页（`main.dart.js`），抓不到界面文字，只能看截图或用导出的 json。它的 GitHub 仓库 2026-07-23 已归档（只读），网页还能用。
- **截图取色要转色彩空间**：macOS 截图带 Display P3 的 ICC 配置，直接读像素会偏（#8DCF4A 读成 #9BCD5E）。用 Pillow 的 `ImageCms.profileToProfile` 转成 sRGB 再读。
- **Compose 没传的角色会落回默认紫色**：`darkColorScheme(...)` 没传的参数用 Material 默认值，所以要写全 48 个角色。`kotlin` 命令会写全。
- **Compose 没有 `shadow` 参数**：导出有 49 个键，Compose 是 48 个参数（没有 `shadow`）。
