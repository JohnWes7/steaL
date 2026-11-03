"""itch.io 来源：读官方博客 RSS 里最近的帖子、预处理成纯文本、按帖子写的时刻（没写就用默认）补时刻。
itch.io source: read recent posts from the official blog RSS, reduce them to text, and apply the times the posts give
(or a default when they don't).

`itch.io/blog.rss` 只有 itch.io 官方的帖子，全站促销都在这里公告，一般开始当天或提前几天发。
`itch.io/blog.rss` only carries itch.io's own posts; site-wide sales are announced there, usually on the day or a few days ahead.
"""

import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from datetime import UTC, date, datetime, time, timedelta
from email.utils import parsedate_to_datetime
from zoneinfo import ZoneInfo

import httpx
from bs4 import BeautifulSoup

from .html_text import element_to_text
from .models import CamelModel, ExtractedEvent, SaleEvent
from .normalize import local_moment_to_utc, make_event_id

STORE = "itchio"
KIND = "sale"
"""只收 itch.io 的全站促销，`kind` 都是 `sale`。
Only itch.io's site-wide sales are collected; their `kind` is always `sale`.
"""

FEED_URL = "https://itch.io/blog.rss"
USER_AGENT = "steaL-backend/0.1 (sale events calendar)"

RECENT_DAYS = 45
"""只读最近这么多天发的帖子：公告在促销开始前后发，促销最长两周左右，更早的帖子里只有已结束的促销。
Only posts from this many recent days are read: announcements come around the start and sales last about two weeks
at most, so older posts only describe ended sales.
"""

SALE_TIME_ZONE = ZoneInfo("America/Los_Angeles")
"""itch.io 公告用太平洋时间（PT）。
itch.io announcements use Pacific Time (PT).
"""

DEFAULT_LOCAL_TIME = time(hour=10)
"""帖子没写时刻时用的默认时刻（2026 年的公告写的是 10:00 AM PT，但不是每年都一样）。
The default time when a post gives none (2026 announcements say 10:00 AM PT, though it varies by year).
"""

SYSTEM_PROMPT = """\
You copy site-wide sale dates from itch.io's official blog posts. Reply with json only.

Rules:
- Only site-wide sales run by itch.io itself, such as the itch.io Summer Sale, Halloween Sale, Winter Sale or a \
Black Friday sale. Skip Creator Day, bundles, game jams, and sales run by individual creators.
- One entry per sale that has an end date. Skip sales without an end date.
- If the post says the sale is live now and gives no start date, use the "Published" date as the start date.
- "name" must be copied exactly as written in the post, without a leading "The" \
(for example "itch.io Halloween Sale 2026"). Do not translate, shorten or rephrase it.
- "startDate" and "endDate" are YYYY-MM-DD. Dates may omit the year: use the year of the "Published" date, \
and if the end month is before the start month, the end is in the next year.
- "startTime" and "endTime" are 24-hour "HH:MM", copied only when the post gives that time in Pacific Time \
(PT, PST, PDT or Pacific). Otherwise use null. Do not convert time zones.
- If the posts contain no site-wide sale, reply {"events": []}.

Example json output:
{
  "events": [
    {"name": "itch.io Halloween Sale 2026", "startDate": "2026-10-25", "endDate": "2026-11-04", \
"startTime": "10:00", "endTime": "10:00"},
    {"name": "itch.io Summer Sale", "startDate": "2026-06-25", "endDate": "2026-07-06", "startTime": null, "endTime": null}
  ]
}
"""


class ItchioExtractedEvent(ExtractedEvent):
    """AI 从帖子里抄下来的一条促销，多了帖子写明的太平洋时间时刻（没写是 `None`）。
    One sale copied by the AI from a post, plus the Pacific times the post states (`None` when not stated).
    """

    start_time: time | None = None
    end_time: time | None = None


class ItchioExtraction(CamelModel):
    """AI 对一篇帖子一次提取的完整输出。
    The full output of one AI extraction of a post.
    """

    events: list[ItchioExtractedEvent]


@dataclass
class ItchioPost:
    """一篇官方博客帖子：标题、地址、发布日期、正文 HTML。
    One official blog post: title, URL, publish date and body HTML.
    """

    title: str
    url: str
    published: date
    html: str


@dataclass
class FeedEntry:
    """RSS 里的一条：标题、地址、发布时刻。
    One RSS item: title, URL and publish time.
    """

    title: str
    url: str
    published_at: datetime


class ItchioPageError(Exception):
    """RSS 或帖子页的结构变了，找不到需要的字段或正文。
    The RSS or post page layout changed and a field or the body could not be found.
    """


def parse_feed(feed_xml: str) -> list[FeedEntry]:
    """解析博客 RSS。
    Parses the blog RSS.

    - Parameter feed_xml: RSS 的 XML 文本 / The RSS XML text.
    - Returns: 所有条目，顺序同 RSS / All items, in RSS order.
    - Raises: `ItchioPageError` 条目缺字段时 / `ItchioPageError` when an item lacks a field.
    """
    root = ElementTree.fromstring(feed_xml)
    entries = []
    for item in root.iter("item"):
        title = item.findtext("title")
        url = item.findtext("link")
        published_text = item.findtext("pubDate")
        if title is None or url is None or published_text is None:
            raise ItchioPageError("RSS item is missing title, link or pubDate")
        published_at = parsedate_to_datetime(published_text)
        entries.append(FeedEntry(title=title, url=url, published_at=published_at))
    return entries


def select_recent_entries(entries: list[FeedEntry], now: datetime) -> list[FeedEntry]:
    """挑出最近 `RECENT_DAYS` 天发的帖子，新的在前。
    Picks posts published in the last `RECENT_DAYS` days, newest first.

    不按标题筛「Sale」：交给 AI 判断，标题换了说法也不会漏；最近的帖子只有几篇，多调几次 AI 也便宜。
    Titles are not filtered for "Sale": the AI decides, so a reworded title is not missed; there are only a few
    recent posts, so the extra AI calls are cheap.
    """
    cutoff = now - timedelta(days=RECENT_DAYS)
    recent = [entry for entry in entries if entry.published_at >= cutoff]
    return sorted(recent, key=lambda entry: entry.published_at, reverse=True)


def parse_post_body(page_html: str) -> str:
    """从帖子页里取出正文 HTML（`section.post_body`，不含评论）。
    Takes the body HTML out of a post page (`section.post_body`, excluding comments).

    - Raises: `ItchioPageError` 找不到正文时 / `ItchioPageError` when the body is missing.
    """
    soup = BeautifulSoup(page_html, "html.parser")
    body = soup.select_one("section.post_body")
    if body is None:
        raise ItchioPageError("section.post_body not found")
    return str(body)


def fetch_posts(client: httpx.Client, now: datetime) -> list[ItchioPost]:
    """读 RSS，下载最近的帖子正文。
    Reads the RSS and downloads the bodies of recent posts.

    - Parameters:
      - client: 发请求用的 httpx 客户端 / The httpx client used for requests.
      - now: 当前时刻（UTC），决定读哪些帖子 / The current time (UTC), which decides which posts are read.
    - Returns: 最近的帖子，新的在前；没有就是空列表 / Recent posts, newest first; empty if there are none.
    - Raises: `httpx.HTTPError` 请求失败时；`ItchioPageError` 结构变了时 /
      `httpx.HTTPError` when a request fails; `ItchioPageError` when the layout changed.
    """
    headers = {"User-Agent": USER_AGENT}
    feed_response = client.get(FEED_URL, headers=headers, timeout=30, follow_redirects=True)
    feed_response.raise_for_status()
    entries = parse_feed(feed_response.text)
    recent_entries = select_recent_entries(entries, now)

    posts = []
    for entry in recent_entries:
        page_response = client.get(entry.url, headers=headers, timeout=30, follow_redirects=True)
        page_response.raise_for_status()
        body_html = parse_post_body(page_response.text)
        published_day = entry.published_at.astimezone(UTC).date()
        post = ItchioPost(title=entry.title, url=entry.url, published=published_day, html=body_html)
        posts.append(post)
    return posts


def extract_text(post: ItchioPost) -> str:
    """把一篇帖子转成纯文本，开头是标题和发布日期（AI 用发布日期补年份和缺的开始日期）。
    Reduces a post to text that starts with its title and publish date (the AI uses the date for missing years and starts).
    """
    soup = BeautifulSoup(post.html, "html.parser")
    body = element_to_text(soup)
    return f"# {post.title}\nPublished: {post.published.isoformat()}\n\n{body}"


def to_sale_event(extracted: ItchioExtractedEvent, post: ItchioPost) -> SaleEvent:
    """把 AI 抄的日期和时刻转成接口的活动：帖子写了太平洋时间就用它，没写用默认 10:00。
    Builds an API event from the AI's dates and times: the post's Pacific time when stated, otherwise 10:00.
    """
    start_time = extracted.start_time
    if start_time is None:
        start_time = DEFAULT_LOCAL_TIME
    end_time = extracted.end_time
    if end_time is None:
        end_time = DEFAULT_LOCAL_TIME

    start = local_moment_to_utc(extracted.start_date, start_time, SALE_TIME_ZONE)
    end = local_moment_to_utc(extracted.end_date, end_time, SALE_TIME_ZONE)
    event_id = make_event_id(STORE, extracted.start_date, extracted.name)
    return SaleEvent(
        id=event_id,
        store=STORE,
        kind=KIND,
        name=extracted.name,
        start=start,
        end=end,
        source_url=post.url,
    )
