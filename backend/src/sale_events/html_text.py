"""与商店无关的 HTML 转纯文本：标题、表格、段落都保留结构，给 AI 读。
Store-independent HTML to plain text that keeps headings, tables and paragraphs readable for the AI.
"""

import re

from bs4 import Tag

from .normalize import collapse_spaces


def element_to_text(element: Tag) -> str:
    """把一段正文 HTML 转成纯文本：标题写成 `## 标题`、表格行写成 `| a | b |`、每段一行。
    Converts a block of body HTML to text: headings become `## Heading`, table rows `| a | b |`, one line per paragraph.

    会原地改动传入的元素，调用方传一份自己的正文。
    Modifies the element in place, so callers pass their own copy of the body.

    - Parameter element: 正文所在的元素 / The element that holds the body.
    - Returns: 预处理后的纯文本 / The reduced plain text.
    """
    for line_break in element.find_all("br"):
        line_break.replace_with("\n")

    for row in element.find_all("tr"):
        cells = row.find_all(["th", "td"])
        cell_texts = [collapse_spaces(cell.get_text()) for cell in cells]
        non_empty_cells = [text for text in cell_texts if text != ""]
        if len(non_empty_cells) == 0:
            row.decompose()
            continue
        row_text = " | ".join(cell_texts)
        row.replace_with(f"| {row_text} |\n")

    for heading in element.find_all(["h1", "h2", "h3", "h4"]):
        heading_text = collapse_spaces(heading.get_text())
        heading.replace_with(f"\n## {heading_text}\n")

    for block in element.find_all(["p", "li"]):
        block_lines = block.get_text().split("\n")
        collapsed_lines = [collapse_spaces(line) for line in block_lines]
        block_text = "\n".join(collapsed_lines)
        block.replace_with(f"{block_text}\n")

    raw_text = element.get_text()
    lines = raw_text.splitlines()
    stripped_lines = [line.strip() for line in lines]
    joined = "\n".join(stripped_lines)
    # 连续空行压成一个。
    # Collapse runs of blank lines into one.
    collapsed = re.sub(r"\n{3,}", "\n\n", joined)
    return collapsed.strip()
