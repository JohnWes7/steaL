"""Steam 来源：抓 Steamworks「Upcoming Steam Events」页、预处理成纯文本、按 Steam 规则补时刻。
Steam source: fetch the Steamworks "Upcoming Steam Events" page, reduce it to text, and apply Steam's timing rules.
"""

import re
from datetime import time
from zoneinfo import ZoneInfo

import httpx
from bs4 import BeautifulSoup

from .models import ExtractedEvent, SaleEvent
from .normalize import local_moment_to_utc, make_event_id

STORE = "steam"
SOURCE_URL = "https://partner.steamgames.com/doc/marketing/upcoming_events"
USER_AGENT = "steaL-backend/0.1 (sale events calendar)"

SALE_TIME_ZONE = ZoneInfo("America/Los_Angeles")
"""Steam 活动的时区：各活动子页与 Discounting 页都写「10am Pacific」。
Steam's event time zone: event sub-pages and the Discounting page all say "10am Pacific".
"""

SALE_LOCAL_TIME = time(hour=10)
"""Steam 活动开始 / 结束的当地时刻。
The local time at which Steam events start and end.
"""


class SteamPageError(Exception):
    """页面结构变了，找不到正文。
    The page layout changed and the main content could not be found.
    """


def fetch_page(client: httpx.Client) -> str:
    """下载 Steamworks 页面的 HTML。
    Downloads the Steamworks page HTML.

    - Parameter client: 发请求用的 httpx 客户端 / The httpx client used for the request.
    - Returns: 页面 HTML / The page HTML.
    - Raises: `httpx.HTTPError` 请求失败时 / `httpx.HTTPError` when the request fails.
    """
    headers = {"User-Agent": USER_AGENT}
    response = client.get(SOURCE_URL, headers=headers, timeout=30, follow_redirects=True)
    response.raise_for_status()
    return response.text


def extract_text(page_html: str) -> str:
    """只取正文、去掉标签，保留标题和表格行的结构。
    Keeps only the main content, strips tags, and keeps headings and table rows readable.

    标题写成 `## 标题`、表格每行写成 `| a | b |`，AI 才分得清在哪一节、哪一年的表格。
    Headings become `## Heading` and rows `| a | b |` so the AI can tell the section and the year of each table.

    - Parameter page_html: 整页 HTML / The full page HTML.
    - Returns: 预处理后的纯文本 / The reduced plain text.
    - Raises: `SteamPageError` 找不到正文时 / `SteamPageError` when the main content is missing.
    """
    soup = BeautifulSoup(page_html, "html.parser")
    content = soup.find("div", class_="documentation_bbcode")
    if content is None:
        raise SteamPageError("documentation_bbcode not found")

    # 表格的日期格是「Oct 26<br>Nov 2」，换成和其他节一样的「Oct 26 - Nov 2」。
    # Table date cells are "Oct 26<br>Nov 2"; write them as ranges like the other sections.
    cell_line_breaks = content.select("td br, th br")
    for line_break in cell_line_breaks:
        line_break.replace_with(" - ")

    for row in content.find_all("tr"):
        cells = row.find_all(["th", "td"])
        cell_texts = [collapse_spaces(cell.get_text()) for cell in cells]
        row_text = " | ".join(cell_texts)
        row.replace_with(f"| {row_text} |\n")

    for line_break in content.find_all("br"):
        line_break.replace_with("\n")

    for heading in content.find_all("h2"):
        heading_text = collapse_spaces(heading.get_text())
        heading.replace_with(f"\n\n## {heading_text}\n")

    raw_text = content.get_text()
    lines = raw_text.splitlines()
    stripped_lines = [line.strip() for line in lines]
    joined = "\n".join(stripped_lines)
    # 连续空行压成一个。
    # Collapse runs of blank lines into one.
    collapsed = re.sub(r"\n{3,}", "\n\n", joined)
    return collapsed.strip()


def collapse_spaces(text: str) -> str:
    """把连续空白（含换行）压成一个空格，去掉首尾空白。
    Collapses runs of whitespace (including newlines) into single spaces and trims the ends.

    不用 `get_text(strip=True)`：它会把每段文字的首尾空格去掉再拼起来，`going <i>fast</i>` 会变成 `goingfast`。
    `get_text(strip=True)` is avoided because it trims each fragment before joining, turning `going <i>fast</i>` into `goingfast`.
    """
    words = text.split()
    return " ".join(words)


def to_sale_event(extracted: ExtractedEvent) -> SaleEvent:
    """把 AI 抄的日期按 Steam 规则补上时刻，转成接口的活动。
    Applies Steam's timing rule to the AI's dates and builds an API event.

    开始和结束都是当天 10:00 太平洋时间；`end` 是结束的那一刻（不含）。
    Both start and end are 10:00 Pacific on that day; `end` is the exclusive end moment.
    """
    start = local_moment_to_utc(extracted.start_date, SALE_LOCAL_TIME, SALE_TIME_ZONE)
    end = local_moment_to_utc(extracted.end_date, SALE_LOCAL_TIME, SALE_TIME_ZONE)
    event_id = make_event_id(STORE, extracted.start_date, extracted.name)
    return SaleEvent(
        id=event_id,
        store=STORE,
        kind=extracted.section,
        name=extracted.name,
        start=start,
        end=end,
        source_url=SOURCE_URL,
    )
