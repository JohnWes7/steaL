"""Epic 来源：在 Epic 开发者论坛找季度「Promotional Guide」帖、预处理成纯文本、按 Epic 规则补时刻。
Epic source: find the quarterly "Promotional Guide" posts on Epic's developer forum, reduce them to text, and apply Epic's timing rules.

指南是 Epic 员工写给开发者的官方日程，每季度一篇、提前 1 到 1.5 个月发；论坛是 Discourse，帖子地址加 `.json` 就是 JSON。
The guides are Epic staff's official schedule for developers, one per quarter, posted 1 to 1.5 months ahead;
the forum runs Discourse, so adding `.json` to a topic URL returns JSON.
"""

import re
from dataclasses import dataclass
from datetime import time
from typing import Any
from zoneinfo import ZoneInfo

import httpx
from bs4 import BeautifulSoup

from .html_text import element_to_text
from .models import CamelModel, ExtractedEvent, SaleEvent
from .normalize import local_moment_to_utc, make_event_id

STORE = "epic"
KIND = "sale"
"""Epic 的活动不分类，`kind` 都是 `sale`。
Epic events are not categorised; their `kind` is always `sale`.
"""

FORUM_BASE_URL = "https://forums.unrealengine.com"
SEARCH_QUERY = '"Promotional Guide" order:latest'
USER_AGENT = "steaL-backend/0.1 (sale events calendar)"

GUIDE_TITLE_PATTERN = re.compile(r"^(\d{4}) Q([1-4]) Promotional Guide")
"""指南标题的开头，如「2026 Q4 Promotional Guide || EGS Promotions」，从中取年份和季度。
The start of a guide title, such as "2026 Q4 Promotional Guide || EGS Promotions", giving the year and quarter.
"""

GUIDE_COUNT = 2
"""取最新的几篇指南：当前季度和下一季度（下一季度还没发时是上一季度，已结束的活动会被校验丢掉）。
How many of the newest guides to read: this quarter and the next (or the previous one before the next is posted;
its ended events are dropped by validation).
"""

SALE_TIME_ZONE = ZoneInfo("America/Los_Angeles")
"""Epic 活动的时区。指南只写日期（时刻在开发者后台），时刻按 Epic 官方新闻稿换算出的太平洋时间推断。
Epic's event time zone. Guides only give dates (times live in the developer portal); the time is inferred from Epic's
official press releases converted to Pacific time.
"""

SALE_LOCAL_TIME = time(hour=8)
"""Epic 活动开始 / 结束的当地时刻（太平洋时间早上 8 点，即美东 11 点）。
The local time at which Epic events start and end (8 AM Pacific, i.e. 11 AM Eastern).
"""

SYSTEM_PROMPT = """\
You copy sale dates from an Epic Games Store "Promotional Guide" forum post. Reply with json only.

Rules:
- One entry per sale campaign that has concrete dates. Each campaign is a name followed by a date line \
such as "October 19 - November 2 | 14 Days".
- "name" must be copied exactly as written in the post (for example "Holiday Sale", "Epic Savings: Nov"). \
Do not translate, shorten or rephrase it.
- "startDate" and "endDate" are YYYY-MM-DD. Do not add times or convert time zones.
- The dates have no year: take the year from the title on the first line ("# 2026 Q4 ..." means 2026). \
If the end month is before the start month, the end is in the next year.
- "November 12 - 26" means November 12 to November 26. Ignore ordinal suffixes such as "20th".
- Ignore discount submission deadlines and any other dates that are not campaign dates.

Example json output:
{
  "events": [
    {"name": "Epic Savings: Nov", "startDate": "2026-11-12", "endDate": "2026-11-26"},
    {"name": "Holiday Sale", "startDate": "2026-12-10", "endDate": "2027-01-07"}
  ]
}
"""


class EpicExtraction(CamelModel):
    """AI 对一篇指南一次提取的完整输出。
    The full output of one AI extraction of a guide.
    """

    events: list[ExtractedEvent]


@dataclass
class EpicGuide:
    """一篇季度指南：标题、帖子地址、正文 HTML。
    One quarterly guide: title, topic URL and body HTML.
    """

    title: str
    url: str
    html: str


class EpicSourceError(Exception):
    """找不到指南，或论坛的结构变了。
    No guide was found, or the forum's structure changed.
    """


def select_guide_topics(search_payload: dict[str, Any]) -> list[dict[str, Any]]:
    """从论坛搜索结果里挑出最新的几篇季度指南（按标题里的年份和季度排）。
    Picks the newest quarterly guides from forum search results (ordered by the year and quarter in the title).

    - Parameter search_payload: `search.json` 的响应 / The `search.json` response.
    - Returns: 选中的 topic，新的在前 / The selected topics, newest first.
    """
    guide_topics = []
    for topic in search_payload.get("topics", []):
        match = GUIDE_TITLE_PATTERN.match(topic["title"])
        if match is None:
            continue
        year = int(match.group(1))
        quarter = int(match.group(2))
        guide_topics.append((year, quarter, topic))

    newest_first = sorted(guide_topics, key=lambda entry: (entry[0], entry[1]), reverse=True)
    selected = newest_first[:GUIDE_COUNT]
    return [entry[2] for entry in selected]


def parse_guide(topic_payload: dict[str, Any]) -> EpicGuide | None:
    """把 topic JSON 转成指南；不是 Epic 员工发的返回 `None`。
    Turns a topic JSON into a guide; returns `None` if it was not posted by Epic staff.

    论坛谁都能发帖，只认员工发的，防止有人发一篇假的「Promotional Guide」。
    Anyone can post on the forum, so only staff posts are trusted, in case someone posts a fake "Promotional Guide".
    """
    first_post = topic_payload["post_stream"]["posts"][0]
    if first_post.get("staff") is not True:
        return None
    url = f"{FORUM_BASE_URL}/t/{topic_payload['slug']}/{topic_payload['id']}"
    return EpicGuide(title=topic_payload["title"], url=url, html=first_post["cooked"])


def fetch_guides(client: httpx.Client) -> list[EpicGuide]:
    """搜索论坛、下载最新的几篇指南。
    Searches the forum and downloads the newest guides.

    - Parameter client: 发请求用的 httpx 客户端 / The httpx client used for requests.
    - Returns: 指南，新的在前 / The guides, newest first.
    - Raises: `httpx.HTTPError` 请求失败时；`EpicSourceError` 一篇员工发的指南都没有时 /
      `httpx.HTTPError` when a request fails; `EpicSourceError` when no staff guide is found.
    """
    headers = {"User-Agent": USER_AGENT}
    search_url = f"{FORUM_BASE_URL}/search.json"
    search_response = client.get(search_url, params={"q": SEARCH_QUERY}, headers=headers, timeout=30)
    search_response.raise_for_status()
    topics = select_guide_topics(search_response.json())

    guides = []
    for topic in topics:
        topic_url = f"{FORUM_BASE_URL}/t/{topic['id']}.json"
        topic_response = client.get(topic_url, headers=headers, timeout=30, follow_redirects=True)
        topic_response.raise_for_status()
        guide = parse_guide(topic_response.json())
        if guide is not None:
            guides.append(guide)

    if len(guides) == 0:
        raise EpicSourceError("no staff Promotional Guide found")
    return guides


def extract_text(guide: EpicGuide) -> str:
    """把一篇指南的 HTML 转成纯文本，第一行是标题（AI 从标题取年份）。
    Reduces a guide's HTML to plain text whose first line is the title (the AI takes the year from it).

    各季度的排版不一样（`<h2>` / `<h3>` / 表格 / 只有段落），所以标题、表格、段落都按通用规则转。
    Each quarter is laid out differently (`<h2>` / `<h3>` / tables / plain paragraphs), so headings, tables and
    paragraphs are all converted with generic rules.

    - Parameter guide: 要转换的指南 / The guide to convert.
    - Returns: 预处理后的纯文本 / The reduced plain text.
    """
    soup = BeautifulSoup(guide.html, "html.parser")

    # 横幅图片会留下「image 1600×402 97.5 KB」这类文字。
    # The banner image would leave text such as "image 1600×402 97.5 KB".
    for image_wrapper in soup.select("div.lightbox-wrapper"):
        image_wrapper.decompose()

    body = element_to_text(soup)
    return f"# {guide.title}\n\n{body}"


def to_sale_event(extracted: ExtractedEvent, guide: EpicGuide) -> SaleEvent:
    """把 AI 抄的日期按 Epic 规则补上时刻，转成接口的活动。
    Applies Epic's timing rule to the AI's dates and builds an API event.

    开始和结束都是当天 8:00 太平洋时间；`end` 是结束的那一刻（不含）。来源是这条所在的指南。
    Both start and end are 8:00 Pacific on that day; `end` is the exclusive end moment. The source is the guide it came from.
    """
    start = local_moment_to_utc(extracted.start_date, SALE_LOCAL_TIME, SALE_TIME_ZONE)
    end = local_moment_to_utc(extracted.end_date, SALE_LOCAL_TIME, SALE_TIME_ZONE)
    event_id = make_event_id(STORE, extracted.start_date, extracted.name)
    return SaleEvent(
        id=event_id,
        store=STORE,
        kind=KIND,
        name=extracted.name,
        start=start,
        end=end,
        source_url=guide.url,
    )
