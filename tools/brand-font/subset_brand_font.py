# /// script
# requires-python = ">=3.10"
# dependencies = ["fonttools==4.55.3"]
# ///
"""从 Noto Sans SC Bold 截出品牌头部要用的中文字，生成 Android 打包用的小字体文件。
Cuts the Chinese characters the brand header needs out of Noto Sans SC Bold into a small font bundled with the
Android app.

用法 / Usage（在仓库根 / from the repo root）:

    uv run tools/brand-font/subset_brand_font.py --source /path/to/NotoSansSC-Bold.otf

截取范围是中文翻译文件里出现过的所有字，加上可打印的 ASCII；翻译改了就重新跑一次。
The cut covers every character in the Chinese translation file plus printable ASCII; rerun it whenever the
translation changes.

下载地址和许可证见同目录 README.md。
See README.md in this folder for the download link and the license.
"""

import argparse
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

from fontTools import subset

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_STRINGS = REPO_ROOT / "androidApp/src/main/res/values-b+zh+Hans/strings.xml"
DEFAULT_OUTPUT = REPO_ROOT / "androidApp/src/main/res/font/noto_sans_sc_bold.otf"

# 中文标题里夹着的英文字母、数字也用这个字体画，所以把可打印的 ASCII 一起截进去。
# Latin letters and digits inside a Chinese title are drawn with this font too, so printable ASCII is kept.
PRINTABLE_ASCII = "".join(chr(code) for code in range(0x20, 0x7F))


def collect_characters(strings_path: Path) -> set[str]:
    """返回翻译文件里所有 <string> 用到的字，加上可打印的 ASCII。
    Returns every character used by the <string> entries of the translation file, plus printable ASCII."""
    tree = ElementTree.parse(strings_path)
    texts = [element.text or "" for element in tree.getroot().iter("string")]
    characters = set("".join(texts))
    characters.update(PRINTABLE_ASCII)
    return characters


def subset_font(source: Path, output: Path, characters: set[str]) -> list[str]:
    """把 source 截成只含 characters 的字体写到 output，返回源字体里没有的字。
    Writes a subset of source containing only characters to output, and returns the characters the source lacks."""
    # 1. 读字体，保留全部 OpenType 特性和 name 表（版权、许可证信息在 name 表里）
    # 1. Load the font, keeping every OpenType feature and the name table (copyright and license live there)
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    font = subset.load_font(str(source), options)

    # 2. 找出源字体里没有的字
    # 2. Find characters the source font lacks
    available_code_points = set(font.getBestCmap().keys())
    missing = sorted(character for character in characters if ord(character) not in available_code_points)

    # 3. 截取并保存
    # 3. Subset and save
    subsetter = subset.Subsetter(options)
    subsetter.populate(text="".join(sorted(characters)))
    subsetter.subset(font)
    output.parent.mkdir(parents=True, exist_ok=True)
    subset.save_font(font, str(output), options)
    return missing


def main() -> int:
    parser = argparse.ArgumentParser(description="Subset Noto Sans SC Bold for the brand header.")
    parser.add_argument("--source", type=Path, required=True, help="NotoSansSC-Bold.otf downloaded from noto-cjk")
    parser.add_argument("--strings", type=Path, default=DEFAULT_STRINGS, help="Chinese strings.xml to read")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="font file to write")
    arguments = parser.parse_args()

    characters = collect_characters(arguments.strings)
    missing = subset_font(arguments.source, arguments.output, characters)

    size_kib = arguments.output.stat().st_size / 1024
    print(f"characters: {len(characters)}")
    print(f"output: {arguments.output} ({size_kib:.1f} KiB)")
    if missing:
        print(f"missing in source (fall back to the system font): {''.join(missing)!r}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
