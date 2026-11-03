"""一个商店的一次刷新：抓页面 → 预处理 → AI 提取 → 补时刻 → 校验 → 整份替换。
One refresh of one store: fetch → reduce → AI extraction → apply times → validate → replace the whole store.

Steam 的页面和 Epic 的季度指南都是完整的日程表，没出现就是不存在了，所以整份替换（改期、取消都能自动处理）。
itch.io 读的是最近的全部官方帖子，也整份替换，只是空列表是正常的（没有促销的时候）。
The Steam page and Epic's quarterly guides are complete schedules, so anything missing no longer exists;
each store's data is replaced as a whole. itch.io reads all recent official posts and is also replaced as a whole,
except that an empty list is normal (when no sale is running).
"""

import logging
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Protocol

from pydantic import BaseModel

from . import epic, itchio, steam
from .extraction import ExtractionError
from .models import SaleEvent, SaleEventList
from .storage import EventStore
from .validation import validate_events

logger = logging.getLogger(__name__)


class Extract(Protocol):
    """把正文交给 AI 抄出活动（`DeepSeekExtractor.extract` 的形状），测试时换成假的。
    Sends text to the AI to copy events (the shape of `DeepSeekExtractor.extract`); tests pass a fake.
    """

    def __call__[Result: BaseModel](self, system_prompt: str, page_text: str, result_type: type[Result]) -> Result: ...


@dataclass
class RefreshReport:
    """一次刷新的结果，给命令行和以后的 function 日志用。
    The result of one refresh, for the CLI and later the function logs.
    """

    written: bool
    events: list[SaleEvent] = field(default_factory=list)
    dropped_ended: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)


def refresh_steam(
    fetch_html: Callable[[], str],
    extract: Extract,
    store: EventStore,
    now: datetime,
) -> RefreshReport:
    """刷新 Steam 的活动；校验不过就不覆盖，把 AI 输出存进 rejected。
    Refreshes Steam events; on failure nothing is overwritten and the AI output goes to rejected.

    抓取与 AI 通过参数传入，测试时可以换成假的，不碰网络。
    Fetching and the AI are passed in so tests can replace them without touching the network.

    - Parameters:
      - fetch_html: 返回 Steamworks 页面 HTML / Returns the Steamworks page HTML.
      - extract: 把正文交给 AI，返回抄出来的活动 / Sends the text to the AI and returns the copied events.
      - store: 读写数据的存储 / The storage to read and write.
      - now: 当前时刻（UTC）/ The current time (UTC).
    - Returns: 这次刷新的结果 / The result of this refresh.
    - Raises: 抓取失败或页面结构变了时照常抛出（没有 AI 输出可存）/
      Fetch failures and layout changes propagate (there is no AI output to save).
    """
    # 1. 抓页面：下载 Steamworks「Upcoming Steam Events」页的 HTML。
    page_html = fetch_html()

    # 2. 预处理：只留正文、去掉标签，标题写成 `## 标题`、表格行写成 `| a | b |`。
    page_text = steam.extract_text(page_html)
    logger.info("Steam page reduced to %d characters", len(page_text))

    # 3. AI 提取：把正文交给 AI 抄出活动日期；没给出能用的 JSON 就存进 rejected，不覆盖旧数据。
    try:
        extraction = extract(steam.SYSTEM_PROMPT, page_text, steam.SteamExtraction)
    except ExtractionError as error:
        return reject_extraction(steam.STORE, error, page_text, store, now)

    # 4. 补时刻：按 Steam 规则给每个日期补上太平洋时间 10 点，换成 UTC，生成 id。
    events = [steam.to_sale_event(extracted) for extracted in extraction.events]

    # 5、6. 校验、整份替换：见 `validate_and_write`。
    ai_output = extraction.model_dump(mode="json", by_alias=True)
    return validate_and_write(steam.STORE, events, ai_output, page_text, store, now)


def refresh_epic(
    fetch_guides: Callable[[], list[epic.EpicGuide]],
    extract: Extract,
    store: EventStore,
    now: datetime,
) -> RefreshReport:
    """刷新 Epic 的活动；每篇指南各提取一次，合起来校验，不过就不覆盖。
    Refreshes Epic events; each guide is extracted separately and validated together; on failure nothing is overwritten.

    每篇单独提取，来源网址就是那一篇，年份也只看那一篇的标题。
    Extracting each guide separately makes its URL the source and its title the only place the year comes from.

    - Parameters:
      - fetch_guides: 返回最新的几篇季度指南 / Returns the newest quarterly guides.
      - extract: 把正文交给 AI，返回抄出来的活动 / Sends the text to the AI and returns the copied events.
      - store: 读写数据的存储 / The storage to read and write.
      - now: 当前时刻（UTC）/ The current time (UTC).
    - Returns: 这次刷新的结果 / The result of this refresh.
    - Raises: 抓取失败或找不到指南时照常抛出 / Fetch failures and missing guides propagate.
    """
    # 1. 抓指南：在 Epic 开发者论坛搜出最新的几篇季度 Promotional Guide。
    guides = fetch_guides()

    guide_texts = []
    ai_outputs = []
    events = []
    for guide in guides:
        # 2. 预处理：每篇转成纯文本，第一行是标题（AI 从标题取年份）。
        guide_text = epic.extract_text(guide)
        guide_texts.append(guide_text)
        logger.info("Epic guide %r reduced to %d characters", guide.title, len(guide_text))

        # 3. AI 提取：一篇一次；任何一篇失败都整次作废，不覆盖旧数据。
        try:
            extraction = extract(epic.SYSTEM_PROMPT, guide_text, epic.EpicExtraction)
        except ExtractionError as error:
            return reject_extraction(epic.STORE, error, guide_text, store, now)
        ai_output = extraction.model_dump(mode="json", by_alias=True)
        ai_outputs.append(ai_output)

        # 4. 补时刻：按 Epic 规则补上太平洋时间 8 点，来源填这一篇的地址。
        guide_events = [epic.to_sale_event(extracted, guide) for extracted in extraction.events]
        events.extend(guide_events)

    # 5、6. 校验、整份替换：名字要出现在任意一篇指南里，见 `validate_and_write`。
    page_text = "\n\n".join(guide_texts)
    return validate_and_write(epic.STORE, events, ai_outputs, page_text, store, now)


def refresh_itchio(
    fetch_posts: Callable[[], list[itchio.ItchioPost]],
    extract: Extract,
    store: EventStore,
    now: datetime,
) -> RefreshReport:
    """刷新 itch.io 的全站促销；每篇帖子各提取一次，合起来校验，不过就不覆盖。
    Refreshes itch.io's site-wide sales; each post is extracted separately and validated together; on failure
    nothing is overwritten.

    同一场促销可能先发「下周开始」、再发「已开始 / 延长」，id 相同时以新帖子为准。
    A sale may be announced ahead and then again when live or extended; for the same id the newer post wins.

    - Parameters:
      - fetch_posts: 返回最近的官方帖子，新的在前 / Returns recent official posts, newest first.
      - extract: 把正文交给 AI，返回抄出来的活动 / Sends the text to the AI and returns the copied events.
      - store: 读写数据的存储 / The storage to read and write.
      - now: 当前时刻（UTC）/ The current time (UTC).
    - Returns: 这次刷新的结果 / The result of this refresh.
    - Raises: 抓取失败或页面结构变了时照常抛出 / Fetch failures and layout changes propagate.
    """
    # 1. 抓帖子：读官方博客 RSS，下载最近 45 天的帖子正文（没有就是空的）。
    posts = fetch_posts()

    post_texts = []
    ai_outputs = []
    events = []
    seen_ids = set()
    for post in posts:
        # 2. 预处理：每篇转成纯文本，开头是标题和发布日期。
        post_text = itchio.extract_text(post)
        post_texts.append(post_text)
        logger.info("itch.io post %r reduced to %d characters", post.title, len(post_text))

        # 3. AI 提取：一篇一次，AI 判断是不是全站促销；任何一篇失败都整次作废，不覆盖旧数据。
        try:
            extraction = extract(itchio.SYSTEM_PROMPT, post_text, itchio.ItchioExtraction)
        except ExtractionError as error:
            return reject_extraction(itchio.STORE, error, post_text, store, now)
        ai_output = extraction.model_dump(mode="json", by_alias=True)
        ai_outputs.append(ai_output)

        # 4. 补时刻：帖子写了太平洋时间就用，没写用 10:00；id 已经出现过（更新的帖子写过）就跳过。
        post_events = [itchio.to_sale_event(extracted, post) for extracted in extraction.events]
        for event in post_events:
            if event.id in seen_ids:
                logger.info("itch.io: %s already announced by a newer post, skipped", event.id)
                continue
            seen_ids.add(event.id)
            events.append(event)

    # 5、6. 校验、整份替换：空列表不算失败，也不比上一份的条数，见 `validate_and_write`。
    page_text = "\n\n".join(post_texts)
    return validate_and_write(itchio.STORE, events, ai_outputs, page_text, store, now, complete_schedule=False)


def reject_extraction(
    store_name: str,
    error: ExtractionError,
    page_text: str,
    store: EventStore,
    now: datetime,
) -> RefreshReport:
    """AI 没给出能用的 JSON：把原始输出存进 rejected，不覆盖旧数据。
    The AI gave no usable JSON: saves the raw output to rejected and keeps the old data.
    """
    logger.error("%s extraction failed: %s", store_name, error)
    payload = {
        "errors": [str(error)],
        "rawOutput": error.raw_output,
        "pageText": page_text,
    }
    store.write_rejected(store_name, now, payload)
    return RefreshReport(written=False, errors=[str(error)])


def validate_and_write(
    store_name: str,
    events: list[SaleEvent],
    ai_output: Any,
    page_text: str,
    store: EventStore,
    now: datetime,
    complete_schedule: bool = True,
) -> RefreshReport:
    """校验一个商店的全部活动，通过就整份替换写入，不过就存进 rejected。
    Validates all events of one store; writes them as a whole if they pass, otherwise saves them to rejected.

    - Parameters:
      - store_name: 商店名，也是文件名 / The store name, which is also the file name.
      - events: 已经补好时刻的活动 / Events with times already applied.
      - ai_output: AI 的原始结构化输出，只在失败时存下来 / The AI's structured output, saved only on failure.
      - page_text: 发给 AI 的正文 / The text sent to the AI.
      - store: 读写数据的存储 / The storage to read and write.
      - now: 当前时刻（UTC）/ The current time (UTC).
      - complete_schedule: 来源是不是完整日程表；是的话空列表和条数骤降都算失败 /
        Whether the source is a complete schedule; if so, an empty list or a sudden drop counts as a failure.
    - Returns: 这次刷新的结果 / The result of this refresh.
    """
    # 5. 校验：完整日程表先读上一份的条数（判断条数骤降用），再逐条检查；不过就存进 rejected，不覆盖旧数据。
    previous_count = None
    if complete_schedule:
        previous = store.read(store_name)
        if previous is not None:
            previous_count = len(previous.events)

    outcome = validate_events(events, page_text, previous_count, now, require_events=complete_schedule)
    if not outcome.passed:
        logger.error("%s validation failed: %s", store_name, outcome.errors)
        payload = {
            "errors": outcome.errors,
            "aiOutput": ai_output,
            "pageText": page_text,
        }
        store.write_rejected(store_name, now, payload)
        return RefreshReport(written=False, errors=outcome.errors)

    # 6. 整份替换：留下的活动按开始时间排序，整份覆盖写入。
    sorted_events = sorted(outcome.kept, key=lambda event: event.start)
    event_list = SaleEventList(refreshed_at=now, events=sorted_events)
    store.write(store_name, event_list)
    logger.info("%s: wrote %d events, dropped %d ended", store_name, len(sorted_events), len(outcome.dropped_ended))
    return RefreshReport(written=True, events=sorted_events, dropped_ended=outcome.dropped_ended)
