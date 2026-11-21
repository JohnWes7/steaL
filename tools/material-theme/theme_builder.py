# /// script
# requires-python = ">=3.10"
# dependencies = ["materialyoucolor==3.0.4"]
# ///
"""复现 Material Theme Builder「Color Match」模式的配色算法，生成、导出、比对配色。
Reproduces Material Theme Builder's "Color Match" color algorithm to generate, export and verify color schemes.

用法 / Usage（在仓库根 / from the repo root）:

    # 从核心色生成 Theme Builder 格式的 json（6 套配色 + 5 条色阶）
    # Generate a Theme Builder style json (6 schemes + 5 tonal palettes) from core colors
    uv run tools/material-theme/theme_builder.py generate --primary 8DCF4A --secondary 3D84FF --tertiary 20221C -o out.json

    # 把 json（生成的或网页导出的）转成 Compose 的 ColorScheme 代码
    # Turn a json (generated or exported from the web) into Compose ColorScheme code
    uv run tools/material-theme/theme_builder.py kotlin out.json --name ThemeBuilder

    # 用网页导出的 json 里的核心色重新生成，并逐个比对
    # Regenerate from the core colors in a web export and compare every value
    uv run tools/material-theme/theme_builder.py verify tools/material-theme/examples/steal-2026-11-21.json

算法说明见同目录 algorithm.md，用法与验证范围见 README.md。
See algorithm.md for the algorithm and README.md for usage and what has been verified.
"""

import argparse
import json
import sys
from pathlib import Path

from materialyoucolor.dynamiccolor.dynamic_scheme import DynamicScheme
from materialyoucolor.dynamiccolor.material_dynamic_colors import MaterialDynamicColors
from materialyoucolor.dynamiccolor.variant import Variant
from materialyoucolor.hct import Hct
from materialyoucolor.palettes.tonal_palette import TonalPalette
from materialyoucolor.scheme.scheme_content import SchemeContent

# 2021 和 2025 两版规范对 Color Match 的结果在验证过的例子里相同；固定一版，结果才稳定。
# The 2021 and 2025 specs gave the same Color Match output in the verified example; pin one so output stays stable.
SPEC_VERSION = "2021"

# 网页导出里每套配色的键，顺序同导出（含 Compose 没有的 shadow）。
# The keys of each scheme in a web export, in export order (including shadow, which Compose doesn't have).
EXPORT_ROLES = [
    "primary", "surfaceTint", "onPrimary", "primaryContainer", "onPrimaryContainer",
    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
    "error", "onError", "errorContainer", "onErrorContainer",
    "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
    "outline", "outlineVariant", "shadow", "scrim", "inverseSurface", "inverseOnSurface", "inversePrimary",
    "primaryFixed", "onPrimaryFixed", "primaryFixedDim", "onPrimaryFixedVariant",
    "secondaryFixed", "onSecondaryFixed", "secondaryFixedDim", "onSecondaryFixedVariant",
    "tertiaryFixed", "onTertiaryFixed", "tertiaryFixedDim", "onTertiaryFixedVariant",
    "surfaceDim", "surfaceBright", "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
    "surfaceContainerHigh", "surfaceContainerHighest",
]

# Compose material3 1.5.0 `lightColorScheme` / `darkColorScheme` 的参数，顺序同函数签名。
# The parameters of Compose material3 1.5.0 `lightColorScheme` / `darkColorScheme`, in signature order.
COMPOSE_ROLES = [
    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
    "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
    "surfaceTint", "inverseSurface", "inverseOnSurface",
    "error", "onError", "errorContainer", "onErrorContainer", "outline", "outlineVariant", "scrim",
    "surfaceBright", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
    "surfaceContainerLow", "surfaceContainerLowest", "surfaceDim",
    "primaryFixed", "primaryFixedDim", "onPrimaryFixed", "onPrimaryFixedVariant",
    "secondaryFixed", "secondaryFixedDim", "onSecondaryFixed", "onSecondaryFixedVariant",
    "tertiaryFixed", "tertiaryFixedDim", "onTertiaryFixed", "onTertiaryFixedVariant",
]

# 网页导出的 6 套配色：(json 里的名字, 是否深色, 对比度档, Kotlin 变量名里的对比度词)。
# The 6 schemes of a web export: (name in the json, is dark, contrast level, contrast word in Kotlin names).
SCHEMES = [
    ("light", False, 0.0, ""),
    ("light-medium-contrast", False, 0.5, "MediumContrast"),
    ("light-high-contrast", False, 1.0, "HighContrast"),
    ("dark", True, 0.0, ""),
    ("dark-medium-contrast", True, 0.5, "MediumContrast"),
    ("dark-high-contrast", True, 1.0, "HighContrast"),
]

# 网页导出的色阶里列出的档位。
# The tones listed for each tonal palette in a web export.
PALETTE_TONES = [0, 5, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70, 80, 90, 95, 98, 99, 100]

# 核心色的名字，顺序同网页。
# The core color names, in the web's order.
CORE_COLOR_NAMES = ["primary", "secondary", "tertiary", "error", "neutral", "neutralVariant"]


# MARK: - 颜色格式 / Color formats


def parse_hex(text: str) -> int:
    """把 `#8DCF4A` / `8DCF4A` 转成不透明的 ARGB 整数。
    Converts `#8DCF4A` / `8DCF4A` into an opaque ARGB integer.
    """
    digits = text.strip().lstrip("#")
    if len(digits) != 6:
        raise ValueError(f"expected a 6-digit hex color, got {text!r}")
    return int("FF" + digits, 16)


def to_hex(argb: int) -> str:
    """把 ARGB 整数转成网页导出用的 `#RRGGBB`（大写）。
    Converts an ARGB integer into the `#RRGGBB` (upper case) used by web exports.
    """
    return "#%06X" % (argb & 0xFFFFFF)


# MARK: - 算法 / Algorithm


def color_match_scheme(core: dict[str, str], is_dark: bool, contrast_level: float) -> dict[str, str]:
    """按 Color Match 算一套配色，返回「角色名 → #RRGGBB」，键同网页导出。
    Computes one Color Match scheme, returning role name → #RRGGBB with the same keys as a web export.

    [core] 至少要有 `primary`；其余核心色给了就用，没给就由 Content 方案从 primary 推出。
    [core] needs at least `primary`; any other core colors given are used, the rest are derived from primary by the
    Content scheme.
    """
    # 1. 主体：以 primary 为源色的 Content 方案，给了的核心色换成它们自己的色阶
    # 1. The base: a Content scheme seeded with primary, with given core colors swapped in as their own palettes
    palette_overrides = {}
    palette_arguments = {
        "secondary": "secondary_palette",
        "tertiary": "tertiary_palette",
        "error": "error_palette",
        "neutral": "neutral_palette",
        "neutralVariant": "neutral_variant_palette",
    }
    for name, argument in palette_arguments.items():
        color = core.get(name)
        if color:
            palette_overrides[argument] = TonalPalette.from_int(parse_hex(color))
    source = Hct.from_int(parse_hex(core["primary"]))
    base = DynamicScheme(
        source, Variant.CONTENT, contrast_level, is_dark, spec_version=SPEC_VERSION, **palette_overrides
    )
    scheme = {role: to_hex(getattr(MaterialDynamicColors, role).get_argb(base)) for role in EXPORT_ROLES}

    # 2. 给了的 secondary / tertiary：以它为源色再跑一次 Content 方案，取 primary 系的四个角色
    # 2. A given secondary / tertiary: run another Content scheme seeded with it and take its four primary roles
    for name in ["secondary", "tertiary"]:
        color = core.get(name)
        if not color:
            continue
        own = SchemeContent(Hct.from_int(parse_hex(color)), is_dark, contrast_level, spec_version=SPEC_VERSION)
        capitalized = name[0].upper() + name[1:]
        pairs = [
            (name, "primary"),
            (f"on{capitalized}", "onPrimary"),
            (f"{name}Container", "primaryContainer"),
            (f"on{capitalized}Container", "onPrimaryContainer"),
        ]
        for role, source_role in pairs:
            scheme[role] = to_hex(getattr(MaterialDynamicColors, source_role).get_argb(own))
    return scheme


def export_palettes(core: dict[str, str]) -> dict[str, dict[str, str]]:
    """算网页导出里的 5 条色阶（只是给人看的参考，配色本身不读它们）。
    Computes the 5 tonal palettes listed in a web export (reference only; the schemes don't read them).

    给了的核心色用它自己的色阶；没给的 secondary / tertiary 取 Content 方案的，neutral / neutralVariant 取
    primary 的色相、彩度 4 / 8。
    Given core colors use their own palettes; missing secondary / tertiary come from the Content scheme, and missing
    neutral / neutralVariant use primary's hue at chroma 4 / 8.
    """
    # 1. 有核心色就用它自己的色阶
    # 1. Use a core color's own palette when it is given
    primary_hct = Hct.from_int(parse_hex(core["primary"]))
    base = SchemeContent(primary_hct, False, 0.0, spec_version=SPEC_VERSION)
    fallbacks = {
        "primary": TonalPalette.from_int(parse_hex(core["primary"])),
        "secondary": base.secondary_palette,
        "tertiary": base.tertiary_palette,
        "neutral": TonalPalette.from_hue_and_chroma(primary_hct.hue, 4.0),
        "neutral-variant": TonalPalette.from_hue_and_chroma(primary_hct.hue, 8.0),
    }
    core_keys = {"neutral-variant": "neutralVariant"}

    # 2. 按导出列出的档位取值
    # 2. Read the tones a web export lists
    palettes = {}
    for name, fallback in fallbacks.items():
        color = core.get(core_keys.get(name, name))
        palette = TonalPalette.from_int(parse_hex(color)) if color else fallback
        palettes[name] = {str(tone): to_hex(palette.tone(tone)) for tone in PALETTE_TONES}
    return palettes


def generate_export(core: dict[str, str]) -> dict:
    """生成和网页导出同样结构的 json 内容（6 套配色 + 5 条色阶）。
    Generates json content with the same structure as a web export (6 schemes + 5 tonal palettes).
    """
    schemes = {name: color_match_scheme(core, is_dark, level) for name, is_dark, level, _ in SCHEMES}
    core_colors = {name: "#" + core[name].lstrip("#").upper() for name in CORE_COLOR_NAMES if core.get(name)}
    return {
        "description": "TYPE: CUSTOM\nGenerated by tools/material-theme/theme_builder.py (Color Match)",
        "seed": core_colors["primary"],
        "coreColors": core_colors,
        "extendedColors": [],
        "schemes": schemes,
        "palettes": export_palettes(core),
    }


# MARK: - Kotlin


def kotlin_scheme(variable: str, is_dark: bool, scheme: dict[str, str]) -> str:
    """把一套配色写成 `private val … = darkColorScheme(…)` 的 Kotlin 代码。
    Writes one scheme as Kotlin code of the form `private val … = darkColorScheme(…)`.
    """
    function = "darkColorScheme" if is_dark else "lightColorScheme"
    lines = [f"private val {variable} = {function}("]
    for role in COMPOSE_ROLES:
        digits = scheme[role].lstrip("#").upper()
        lines.append(f"    {role} = Color(0xFF{digits}),")
    lines.append(")")
    return "\n".join(lines)


def kotlin_from_export(export: dict, name: str, contrasts: list[str]) -> str:
    """把导出 json 里选中对比度的浅色、深色配色都写成 Kotlin 代码，变量名如 `ThemeBuilderDarkColors`。
    Writes the light and dark schemes of the chosen contrast levels as Kotlin, with names like
    `ThemeBuilderDarkColors`.
    """
    contrast_words = {"standard": "", "medium": "MediumContrast", "high": "HighContrast"}
    wanted_words = {contrast_words[contrast] for contrast in contrasts}
    blocks = []
    for scheme_name, is_dark, _, word in SCHEMES:
        if word not in wanted_words:
            continue
        mode = "Dark" if is_dark else "Light"
        variable = f"{name}{word}{mode}Colors"
        blocks.append(kotlin_scheme(variable, is_dark, export["schemes"][scheme_name]))
    return "\n\n".join(blocks) + "\n"


# MARK: - 比对 / Verification


def compare_exports(expected: dict, actual: dict) -> list[str]:
    """逐个比对两份导出的配色和色阶，返回不一致的描述（空列表表示完全一致）。
    Compares every scheme and palette value of two exports, returning descriptions of the differences (empty when
    identical).
    """
    differences = []
    for scheme_name, expected_scheme in expected.get("schemes", {}).items():
        actual_scheme = actual["schemes"].get(scheme_name, {})
        for role, expected_value in expected_scheme.items():
            actual_value = actual_scheme.get(role, "(missing)")
            if actual_value.upper() != expected_value.upper():
                differences.append(f"schemes.{scheme_name}.{role}: expected {expected_value}, got {actual_value}")
    for palette_name, expected_palette in expected.get("palettes", {}).items():
        actual_palette = actual["palettes"].get(palette_name, {})
        for tone, expected_value in expected_palette.items():
            actual_value = actual_palette.get(tone, "(missing)")
            if actual_value.upper() != expected_value.upper():
                differences.append(f"palettes.{palette_name}.{tone}: expected {expected_value}, got {actual_value}")
    return differences


# MARK: - 命令行 / Command line


def core_from_arguments(arguments: argparse.Namespace) -> dict[str, str]:
    """从命令行参数取出核心色（没给的不放进去）。
    Collects the core colors from the command-line arguments, leaving out the ones not given.
    """
    values = {
        "primary": arguments.primary,
        "secondary": arguments.secondary,
        "tertiary": arguments.tertiary,
        "error": arguments.error,
        "neutral": arguments.neutral,
        "neutralVariant": arguments.neutral_variant,
    }
    return {name: value for name, value in values.items() if value}


def run_generate(arguments: argparse.Namespace) -> int:
    """`generate`：核心色 → 网页导出格式的 json。
    `generate`: core colors → a json in the web export format.
    """
    export = generate_export(core_from_arguments(arguments))
    text = json.dumps(export, indent=4, ensure_ascii=False) + "\n"
    if arguments.output:
        Path(arguments.output).write_text(text, encoding="utf-8")
    else:
        sys.stdout.write(text)
    return 0


def run_kotlin(arguments: argparse.Namespace) -> int:
    """`kotlin`：json → Compose 的 ColorScheme 代码，打印到标准输出。
    `kotlin`: json → Compose ColorScheme code, printed to standard output.
    """
    export = json.loads(Path(arguments.json).read_text(encoding="utf-8"))
    contrasts = ["standard", "medium", "high"] if arguments.contrast == "all" else [arguments.contrast]
    sys.stdout.write(kotlin_from_export(export, arguments.name, contrasts))
    return 0


def run_verify(arguments: argparse.Namespace) -> int:
    """`verify`：用导出 json 里的核心色重新生成，逐个比对，一致返回 0。
    `verify`: regenerates from the core colors in an exported json and compares every value; returns 0 when identical.
    """
    # 1. 读导出的核心色，重新生成
    # 1. Read the export's core colors and regenerate
    expected = json.loads(Path(arguments.json).read_text(encoding="utf-8"))
    core = {name: value for name, value in expected["coreColors"].items() if value}
    actual = generate_export(core)

    # 2. 逐个比对，打印结果
    # 2. Compare every value and print the result
    differences = compare_exports(expected, actual)
    total_schemes = sum(len(scheme) for scheme in expected.get("schemes", {}).values())
    total_palettes = sum(len(palette) for palette in expected.get("palettes", {}).values())
    print(f"core colors: {core}")
    print(f"compared {total_schemes} scheme values and {total_palettes} palette values")
    if not differences:
        print("all identical")
        return 0
    for difference in differences:
        print(difference)
    print(f"{len(differences)} differences")
    return 1


def main() -> int:
    """命令行入口，返回进程退出码。
    The command-line entry point; returns the process exit code.
    """
    parser = argparse.ArgumentParser(description="Reproduce Material Theme Builder (Color Match) color schemes.")
    commands = parser.add_subparsers(dest="command", required=True)

    generate = commands.add_parser("generate", help="core colors -> Theme Builder style json")
    generate.add_argument("--primary", required=True, help="primary / source color, e.g. 8DCF4A")
    generate.add_argument("--secondary", help="secondary core color (optional)")
    generate.add_argument("--tertiary", help="tertiary core color (optional)")
    generate.add_argument("--error", help="error core color (optional, not verified against the web)")
    generate.add_argument("--neutral", help="neutral core color (optional, not verified against the web)")
    generate.add_argument("--neutral-variant", help="neutral variant core color (optional, not verified)")
    generate.add_argument("-o", "--output", help="output json path (default: stdout)")
    generate.set_defaults(handler=run_generate)

    kotlin = commands.add_parser("kotlin", help="json -> Compose ColorScheme code")
    kotlin.add_argument("json", help="a generated or web-exported json")
    kotlin.add_argument("--name", required=True, help="variable name prefix, e.g. ThemeBuilder")
    kotlin.add_argument("--contrast", choices=["standard", "medium", "high", "all"], default="standard")
    kotlin.set_defaults(handler=run_kotlin)

    verify = commands.add_parser("verify", help="regenerate a web export from its core colors and compare")
    verify.add_argument("json", help="a json exported from the Theme Builder website with Color Match on")
    verify.set_defaults(handler=run_verify)

    arguments = parser.parse_args()
    return arguments.handler(arguments)


if __name__ == "__main__":
    sys.exit(main())
