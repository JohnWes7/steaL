"""sale_events_api：读各商店的活动文件，合并成 `GET /v1/sale-events` 的响应。
sale_events_api: reads each store's event file and merges them into the `GET /v1/sale-events` response.

只用标准库、不 import `sale_events`：文件内容当字典处理，包小、冷启动快。
Standard library only and no `sale_events` import: file contents are handled as dicts, keeping the package small and cold starts fast.

与云无关：怎么读文件由调用方传入（本地读 `.data/`，上云后读对象存储），结果是普通的 `Response`，
由各家云的 handler 转成自己的格式。
Cloud-neutral: how files are read is passed in (local `.data/` now, object storage later), and the result is a plain
`Response` that each cloud's handler converts to its own format.
"""

import json
import logging
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import datetime

logger = logging.getLogger(__name__)

CACHE_CONTROL = "public, max-age=3600"
"""成功响应的缓存头：数据每周才刷新一次。
The cache header for successful responses: the data only refreshes weekly.
"""


@dataclass
class Response:
    """与云无关的 HTTP 响应。
    A cloud-neutral HTTP response.
    """

    status: int
    body: str
    headers: dict[str, str] = field(default_factory=dict)


class NoDataError(Exception):
    """还没有任何一次成功的刷新（一个商店文件都没有）。
    No refresh has succeeded yet (there is no store file at all).
    """


def parse_time(text: str) -> datetime:
    """解析接口里的 RFC 3339 UTC 时刻（`2026-12-17T18:00:00Z`）。
    Parses an RFC 3339 UTC time from the API data (`2026-12-17T18:00:00Z`).
    """
    return datetime.fromisoformat(text)


def merge_store_files(file_texts: list[str], now: datetime) -> dict:
    """把各商店的文件合并成一份响应：去掉已结束的，按开始时间排序。
    Merges the store files into one response: drops ended events and sorts by start.

    `refreshedAt` 取各文件里最早的那个：客户端看到的是「最旧的那部分数据有多旧」。
    `refreshedAt` is the earliest among the files, so clients see how old the stalest part is.

    文件之间可能隔几天才刷新，期间有活动结束，所以读的时候按 `now` 再滤一次。
    Files refresh days apart and events end in between, so ended events are filtered again at read time.

    - Parameters:
      - file_texts: 每个商店文件的 JSON 文本 / The JSON text of each store file.
      - now: 当前时刻（UTC）/ The current time (UTC).
    - Returns: `{ refreshedAt, events }` 形状的字典 / A dict shaped `{ refreshedAt, events }`.
    - Raises: `NoDataError` 一个文件都没有时 / `NoDataError` when there are no files.
    """
    if len(file_texts) == 0:
        raise NoDataError("no store files")

    store_lists = [json.loads(text) for text in file_texts]

    refreshed_times = [store_list["refreshedAt"] for store_list in store_lists]
    earliest_refreshed = min(refreshed_times, key=parse_time)

    all_events = []
    for store_list in store_lists:
        all_events.extend(store_list["events"])

    upcoming_events = [event for event in all_events if parse_time(event["end"]) > now]
    sorted_events = sorted(upcoming_events, key=lambda event: parse_time(event["start"]))

    return {"refreshedAt": earliest_refreshed, "events": sorted_events}


def problem_response(status: int, title: str, detail: str) -> Response:
    """生成 RFC 9457 Problem Details 错误响应。
    Builds an RFC 9457 Problem Details error response.
    """
    payload = {"type": "about:blank", "title": title, "status": status, "detail": detail}
    body = json.dumps(payload)
    headers = {"Content-Type": "application/problem+json"}
    return Response(status=status, body=body, headers=headers)


def list_sale_events(read_store_files: Callable[[], list[str]], now: datetime) -> Response:
    """处理 `GET /v1/sale-events`。
    Handles `GET /v1/sale-events`.

    读不到存储、还没有数据都是 503（暂时不可用，客户端稍后重试）；其他意外是 500。
    Unreadable storage and no data yet are both 503 (temporarily unavailable); anything else unexpected is 500.

    - Parameters:
      - read_store_files: 返回每个商店文件的 JSON 文本 / Returns the JSON text of each store file.
      - now: 当前时刻（UTC）/ The current time (UTC).
    - Returns: 要发给客户端的响应 / The response to send to the client.
    """
    try:
        file_texts = read_store_files()
    except OSError as error:
        logger.error("Reading store files failed: %s", error)
        return problem_response(503, "Service Unavailable", "Sale events storage is unreadable.")

    try:
        merged = merge_store_files(file_texts, now)
    except NoDataError:
        logger.error("No store files yet")
        return problem_response(503, "Service Unavailable", "No sale events have been refreshed yet.")
    except (ValueError, KeyError, TypeError) as error:
        # 文件是 refresh 校验过才写的，解析失败说明数据坏了。
        # Files are only written after validation, so a parse failure means corrupted data.
        logger.error("Merging store files failed: %r", error)
        return problem_response(500, "Internal Server Error", "Sale events data is corrupted.")

    body = json.dumps(merged, ensure_ascii=False)
    headers = {"Content-Type": "application/json", "Cache-Control": CACHE_CONTROL}
    return Response(status=200, body=body, headers=headers)
