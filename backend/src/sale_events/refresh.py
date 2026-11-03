"""Steam 的一次刷新：抓页面 → 预处理 → AI 提取 → 补时刻 → 校验 → 整份替换。
One Steam refresh: fetch → reduce → AI extraction → apply times → validate → replace the whole store.

Steam 的页面是完整的日程表，没出现在页面上就是不存在了，所以整份替换（改期、取消都能自动处理）。
The Steam page is a complete schedule, so anything missing from it no longer exists; the store is replaced as a whole.
"""

import logging
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import datetime

from . import steam
from .extraction import ExtractionError
from .models import ExtractionResult, SaleEvent, SaleEventList
from .storage import EventStore
from .validation import validate_events

logger = logging.getLogger(__name__)


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
    extract: Callable[[str], ExtractionResult],
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
        extraction = extract(page_text)
    except ExtractionError as error:
        logger.error("Steam extraction failed: %s", error)
        payload = {
            "errors": [str(error)],
            "rawOutput": error.raw_output,
            "pageText": page_text,
        }
        store.write_rejected(steam.STORE, now, payload)
        return RefreshReport(written=False, errors=[str(error)])

    # 4. 补时刻：按 Steam 规则给每个日期补上太平洋时间 10 点，换成 UTC，生成 id。
    events = [steam.to_sale_event(extracted) for extracted in extraction.events]

    # 5. 校验：先读上一份的条数（判断条数骤降用），再逐条检查；不过就存进 rejected，不覆盖旧数据。
    previous = store.read(steam.STORE)
    previous_count = None
    if previous is not None:
        previous_count = len(previous.events)

    outcome = validate_events(events, page_text, previous_count, now)
    if not outcome.passed:
        logger.error("Steam validation failed: %s", outcome.errors)
        ai_output = extraction.model_dump(mode="json", by_alias=True)
        payload = {
            "errors": outcome.errors,
            "aiOutput": ai_output,
            "pageText": page_text,
        }
        store.write_rejected(steam.STORE, now, payload)
        return RefreshReport(written=False, errors=outcome.errors)

    # 6. 整份替换：留下的活动按开始时间排序，整份覆盖写入。
    sorted_events = sorted(outcome.kept, key=lambda event: event.start)
    event_list = SaleEventList(refreshed_at=now, events=sorted_events)
    store.write(steam.STORE, event_list)
    logger.info("Steam: wrote %d events, dropped %d ended", len(sorted_events), len(outcome.dropped_ended))
    return RefreshReport(written=True, events=sorted_events, dropped_ended=outcome.dropped_ended)
