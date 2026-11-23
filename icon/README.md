# App Icon 源文件

steaL 的图标：深蓝渐变背景 + 三缕蒸汽 + 绿色折扣标签（带 % 号）。各平台共用这里的图层。

```
icon/
  layers/          图层源文件（1024×1024 画布、透明底，每个文件一层）
    1-steam.svg      蒸汽（白色描边，90% 不透明）
    2-tag.svg        标签（品牌绿 #8DCF4A，绳孔用 mask 挖空）
    3-percent.svg    % 号（深绿 #14301C 描边，用图形画、不依赖字体）
  AppIcon.icon/    Icon Composer 文档包（iOS），和 iosApp/steaL/AppIcon.icon 保持一致
  preview.svg      带背景和圆角的平面合成图，只用来看布局，不是最终效果
```

| 项目 | 值 |
| --- | --- |
| 背景 | 从上到下线性渐变 #2A3F5A → #121C28（终点在 70% 高度处） |
| 图层顺序（前 → 后） | `1-steam` → `3-percent` → `2-tag`（各自单独一个 group） |
| 蒸汽位置 | 在 `icon.json` 里上移 25pt（`translation-in-points: [0, -25]`），SVG 本身没动 |
| 标签 | SVG 里自带 `scale(1.12)` |

## 改图标的流程（iOS）

1. 改 `layers/*.svg`。
2. 复制到两个 `.icon` 包里（包里存的是副本，不会自动同步）：
   ```sh
   cp icon/layers/*.svg icon/AppIcon.icon/Assets/
   cp icon/layers/*.svg iosApp/steaL/AppIcon.icon/Assets/
   ```
3. 改了 `icon.json` 的参数（在 Icon Composer 里改或手改）也要两边一致：
   `cp iosApp/steaL/AppIcon.icon/icon.json icon/AppIcon.icon/`。
4. `preview.svg` 是手写的合成图，图层或位置改了要手动跟着改（蒸汽组的 `translate(0 -25)` 对应 `icon.json` 的上移）。
5. 重新导出 App 内的图标快照（见下面「App 内显示的图标副本」）。

## 命令行渲染（不用开 Icon Composer）

`icon-composer-mcp` 这个 CLI 调 Apple 的 `ictool` 出图，改完 SVG / icon.json 立刻能看结果：

```sh
npm install -g icon-composer-mcp          # 依赖 Xcode 自带的 ictool
icon-composer doctor                      # 检查环境
icon-composer render icon/AppIcon.icon out.png
icon-composer render icon/AppIcon.icon out-dark.png --rendition Dark      # 深色外观
icon-composer render icon/AppIcon.icon out-small.png --width 180 --height 180
icon-composer inspect icon/AppIcon.icon   # 看当前参数
```

**不要用 `qlmanage` 判断效果**：它只渲染平面 SVG，看不到 Liquid Glass。

项目根目录的 `.mcp.json` 把它也注册成了 MCP server（已 gitignore）。

### App 内显示的图标副本（`AppIconImage`）

登录 sheet 顶部显示的是图标的静态 PNG：`iosApp/steaL/Assets.xcassets/AppIconImage.imageset/AppIconImage.png`
（代码里是 `Image(.appIcon)`，Xcode 生成符号时去掉了名字末尾的 "Image"）。App Icon 本身在代码里读不到，
Liquid Glass 也只有系统能实时渲染，所以只能放一张快照。`render` 出来的图已经带圆角遮罩和透明角，不用再裁。
只用默认外观一张，不做深色版。**改了 `.icon` 之后要重新导出**，否则 App 里和主屏幕上的图标会不一样：

```sh
icon-composer render icon/AppIcon.icon /tmp/icon.png
sips -Z 192 /tmp/icon.png --out iosApp/steaL/Assets.xcassets/AppIconImage.imageset/AppIconImage.png   # 64pt @3x
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
- **Fill 留 Automatic = 用 SVG 自带颜色**，不会被当成可染色的玻璃材质；要彩色玻璃就手动指定颜色或渐变。
- **Shadow / Specular / Translucency / Lighting 在 Group 这一级**（`icon.json` 里这几个 key 挂在 group 上），
  图层这一级只有 Opacity / Blend Mode / Fill / Effects / Image / Layout。根节点只有背景 Fill。
- 各区块右边的 `All ⌄` 表示这个设置应用到哪个外观（All / Default / Dark / Tinted）。
- **左边面板越靠上 = 越靠前**（Group 之间同理）。
- 挖孔再把另一层嵌进洞里（试过把 % 号做成宝石嵌进标签）很丑，像补丁；Apple 自家图标（Home、Passwords）
  都是互相独立的图层直接堆叠，靠各自的阴影 / 高光分前后。
- **白色 Fill + specular + translucency 拉满 = 铬合金**，不是彩色玻璃。玻璃材质不自带颜色，
  颜色全来自 Fill；要彩色玻璃就指定饱和颜色，并把 translucency 降到 0.3～0.5。
- **细节尺寸要留够余量**：玻璃会给形状加厚，细小的洞（如 % 圆环中间）会糊死。

## 背景怎么设（Icon Composer）

Color 选 **Default** 外观 → Fill 选 **Gradient** → 下面那格（渐变底部）填 `#121C28`，
上面那格（顶部）填 `#2A3F5A`。
