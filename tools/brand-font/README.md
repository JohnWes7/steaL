# brand-font

给 Android 品牌头部（`BrandHeader`：tab 根页面顶栏的大标题和标语）生成中文字体文件。

为什么要它：品牌头部的英文用打包的 Roboto Bold，不跟手机的系统字体变；但 Roboto 没有中文字，中文会退回系统字体（Pixel 上是 Noto Sans CJK，小米上是 MiSans，粗细也不一样）。所以中文也打包一个固定字体：**Noto Sans SC Bold**（思源黑体，和 Pixel 的系统中文字体同一套）。完整字体约 8.5 MB，这个脚本只截取用得到的字，生成的文件不到 100 KB。

## 截取范围

`androidApp/src/main/res/values-b+zh+Hans/strings.xml` 里所有 `<string>` 用到的字，加上可打印的 ASCII（中文标题里夹的英文字母、数字也用这个字体画）。

**中文翻译改了就重新跑一次。** 忘了跑也不会崩：缺的字由系统字体显示，只是那几个字的字形和别的字不一样。

## 用法

前提：装了 [uv](https://docs.astral.sh/uv/)。脚本头写了依赖（`fonttools==4.55.3`），`uv run` 会自动装到临时环境。

1. 下载源字体（不进仓库）：

   ```sh
   curl -L -o /tmp/NotoSansSC-Bold.otf \
       https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/SubsetOTF/SC/NotoSansSC-Bold.otf
   ```

   2026-11-24 用的是 Version 2.004（字体 name 表里的版本）。

2. 在仓库根运行：

   ```sh
   uv run tools/brand-font/subset_brand_font.py --source /tmp/NotoSansSC-Bold.otf
   ```

   输出 `androidApp/src/main/res/font/noto_sans_sc_bold.otf`，并打印截了多少字、文件多大；源字体里没有的字会列在 stderr。`--strings` / `--output` 可以换输入输出路径。

## 许可证

Noto Sans SC 是 SIL Open Font License 1.1（© 2014-2021 Adobe）。许可证原文随 App 打包在 `androidApp/src/main/assets/licenses/NotoSansSC-OFL.txt`（noto-cjk 仓库 `Sans/LICENSE`，开头补了字体 name 表里的版权行）。OFL 允许字体（含修改过的版本）随软件打包、嵌入发布，条件是附上版权声明和许可证（正文「Permission & Conditions」第 2 条）；这份 `LICENSE` 里没有声明保留字体名。以后做「关于 / 开源许可」页要列出它。
