# material-theme

复现 [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/) 「Color Match」模式的配色算法的命令行工具。给几个核心色，就能得到和网页导出**逐个一致**的配色 json，再直接转成 Compose 的 `ColorScheme` 代码。

为什么要它：

- 网页不能脚本化，改一次颜色就要手动导出、手抄色值；这个工具一条命令就出结果。
- 网页导出没法直接给 Compose 用；`kotlin` 命令生成能直接粘进代码的 `lightColorScheme(...)` / `darkColorScheme(...)`，写全 48 个角色。
- 能比对：拿网页导出的 json 跑 `verify`，确认算法没走样，也能用来研究网页的新模式。

算法本身（为什么 primary 会被推亮、没填 Tertiary 为什么整页变薄荷绿、对比度档怎么来的……）见 [algorithm.md](algorithm.md)。

## 前提

装了 [uv](https://docs.astral.sh/uv/)（`backend/` 也在用）。脚本头写了依赖（`materialyoucolor==3.0.4`），第一次 `uv run` 会自动装到临时环境，不用建虚拟环境、不改任何项目文件。

以下命令都在**仓库根**运行。

## 三个命令

### `generate`：核心色 → 配色 json

```sh
uv run tools/material-theme/theme_builder.py generate \
    --primary 8DCF4A --secondary 3D84FF --tertiary 20221C \
    -o /tmp/theme.json
```

- `--primary` 必填（同网页的 Primary，也是源色）；`--secondary`、`--tertiary` 可选，不填由算法从 Primary 推出。
- `--neutral`、`--neutral-variant`、`--error` 可选，**没和网页比对过**（见 algorithm.md 第 4 节）。
- 输出结构同网页导出：`schemes` 里 6 套（标准 / 中 / 高对比度 × 浅 / 深），`palettes` 里 5 条色阶。不写 `-o` 就打到屏幕上。
- 颜色写 `8DCF4A` 或 `#8DCF4A` 都行（带 `#` 时要加引号）。

### `kotlin`：配色 json → Compose 代码

```sh
uv run tools/material-theme/theme_builder.py kotlin /tmp/theme.json --name ThemeBuilder
uv run tools/material-theme/theme_builder.py kotlin /tmp/theme.json --name ThemeBuilder --contrast all
```

- 输入可以是 `generate` 生成的，也可以是网页导出的 json。
- 变量名是 `{name}{对比度}{Light|Dark}Colors`，如 `ThemeBuilderDarkColors`、`ThemeBuilderMediumContrastDarkColors`。
- `--contrast`：`standard`（默认）/ `medium` / `high` / `all`。
- 输出是 `private val … = darkColorScheme(…)`，需要的 import：`androidx.compose.material3.darkColorScheme`、`lightColorScheme`、`androidx.compose.ui.graphics.Color`。

### `verify`：和网页导出逐个比对

```sh
uv run tools/material-theme/theme_builder.py verify tools/material-theme/examples/steal-2026-11-21.json
```

读导出里的 `coreColors`，用脚本重新生成，逐个比对 6 套配色和 5 条色阶。全部一致时打印 `all identical`、退出码 0；不一致时逐条列出、退出码 1。

## 例子：steaL 现在的配色是怎么来的

`androidApp/.../views/theme/ColorPalettes.kt` 里三套配色中的两套由这个工具生成：

```sh
# ThemeBuilder（默认那套）：直接转网页导出
uv run tools/material-theme/theme_builder.py kotlin tools/material-theme/examples/steal-2026-11-21.json --name ThemeBuilder

# ThemeBuilderIosGray：同样的核心色，Neutral / Neutral Variant 填 iOS 卡片灰
uv run tools/material-theme/theme_builder.py generate --primary 8DCF4A --secondary 3D84FF --tertiary 20221C \
    --neutral 27292B --neutral-variant 27292B -o /tmp/ios-gray.json
uv run tools/material-theme/theme_builder.py kotlin /tmp/ios-gray.json --name ThemeBuilderIosGray
```

这两条命令的输出和 `ColorPalettes.kt` 里的代码逐字相同。第三套 `SimulatedC` 是按固定档位手算的，不是 Theme Builder 的算法，不由这个工具生成。

`examples/steal-2026-11-21.json` 是 2026-11-21 从网页导出的原件（Primary #8DCF4A、Secondary #3D84FF、Tertiary #20221C、开 Color Match、Neutral 不填），留着当回归测试：改了脚本先跑一次 `verify`。

## 验证范围

| | 状态 |
|---|---|
| Color Match 打开，填 Primary / Secondary / Tertiary | ✅ 和导出 294 + 90 个值一致 |
| 中 / 高对比度 | ✅ 同上（包含在 6 套里） |
| 不填 Tertiary | ⚠️ 只和网页截图取色对过一个值（深色 `tertiaryContainer`：截图 #00D792，脚本 #00D791，差 1 是截图色彩转换的误差） |
| 不填 Secondary | ❌ 没和网页比对过 |
| 填 Neutral / Neutral Variant / Error | ❌ 没和网页比对过 |
| Color Match 关闭 | ❌ 没实现 |
| 扩展色（Extended colors）、harmonize | ❌ 没实现 |

要补没验证的：在网页上用同样的设置导出 json，放进 `examples/`，跑 `verify`；不一致就改 `color_match_scheme`，一致了再把结论写进 algorithm.md、更新上表。

## 在其他对话里怎么用

CLAUDE.md 的配色一条已经指向这里，新对话做配色时会读到。你也可以直接说，例如：

- 「用 `tools/material-theme` 按 primary=XXXXXX、secondary=XXXXXX 生成一套配色，加进 `ColorPalettes.kt`，叫 Xxx」
- 「这是我从 Theme Builder 新导出的 json，先 verify 一下，再转成 Kotlin 换掉 ThemeBuilder 那套」
- 「给 ThemeBuilder 那套加上中 / 高对比度」（`kotlin --contrast all`）
- 「Theme Builder 网页上 XX 选项是什么效果」：先读 algorithm.md，没写的就让我导出一份 json 来验证

给 AI 的注意事项：

- 色值一律用 `kotlin` 命令生成，不手抄；改完 `ColorPalettes.kt` 要编译一次。
- steaL 的规矩：每套配色写全浅色、深色两份，**彼此不共用值**（不用 `copy()`）；Material 之外的颜色（品牌字标、火焰等）不走这个工具，放 `ExtendedColors`（见 CLAUDE.md「按语义用颜色」）。
- 改了 `theme_builder.py` 先跑 `verify examples/steal-2026-11-21.json`，必须还是 `all identical`。
- 新发现的算法规律写进 algorithm.md，「已验证」只写跑过 `verify` 的。

## 文件

```
tools/material-theme/
  README.md           本文件
  algorithm.md        算法整理：概念、默认档位、Color Match 的真实做法、没验证的部分、踩过的坑
  theme_builder.py    命令行工具（generate / kotlin / verify），单文件，依赖写在文件头
  examples/
    steal-2026-11-21.json   网页导出的原件，回归测试用
```
