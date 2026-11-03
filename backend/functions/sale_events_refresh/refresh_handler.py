"""AWS 专用：refresh Lambda 的入口。每次调用只刷新事件里指定的一个商店。
AWS only: the refresh Lambda's entry point. Each invocation refreshes the one store named in the event.

EventBridge Scheduler 每个商店一个 schedule，事件形如 `{"store": "gog"}`。这里只做 I/O 衔接：读环境变量、建 S3 存储，
其余交给与云无关的 `sale_events.runner`。
EventBridge Scheduler has one schedule per store with events like `{"store": "gog"}`. This file only wires I/O:
it reads environment variables and builds the S3 store; the rest is the cloud-neutral `sale_events.runner`.
"""

import logging
import os
from datetime import UTC, datetime
from typing import Any

import boto3
import httpx
from s3_store import S3EventStore

from sale_events.extraction import DEFAULT_BASE_URL, DEFAULT_MODEL
from sale_events.runner import STORES, DeepSeekSettings, run_store

# Lambda 的 Python 运行时已经给根 logger 装好了输出，只需要把级别调到 INFO。
# The Lambda Python runtime already attaches a handler to the root logger; only the level needs raising to INFO.
logging.getLogger().setLevel(logging.INFO)
logger = logging.getLogger(__name__)


class RefreshFailedError(Exception):
    """这个商店这次没写入（抓取后校验不过或 AI 输出不可用）。抛出它让这次调用算失败，触发失败通知。
    This store was not written this time (validation failed or the AI output was unusable). Raising it marks the
    invocation as failed, which triggers the failure notification.
    """


def handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Lambda 入口：刷新 `event["store"]` 这一个商店。
    Lambda entry point: refreshes the single store named by `event["store"]`.

    - Parameters:
      - event: Scheduler 传来的事件，必须有 `store` / The event from Scheduler, which must contain `store`.
      - context: Lambda 运行上下文（不用）/ The Lambda context (unused).
    - Returns: 写入结果摘要（写进日志，方便看）/ A summary of what was written (for the logs).
    - Raises: `ValueError` 商店名不对时；`RefreshFailedError` 没写入时 /
      `ValueError` for a bad store name; `RefreshFailedError` when nothing was written.
    """
    store_name = event.get("store")
    if store_name not in STORES:
        raise ValueError(f"event must contain store, one of {STORES}; got {store_name!r}")

    model = os.environ.get("DEEPSEEK_MODEL", DEFAULT_MODEL)
    base_url = os.environ.get("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL)
    settings = DeepSeekSettings(api_key=os.environ["DEEPSEEK_API_KEY"], model=model, base_url=base_url)
    s3_client = boto3.client("s3")
    event_store = S3EventStore(s3_client, os.environ["BUCKET_NAME"])
    now = datetime.now(UTC)

    with httpx.Client() as http_client:
        report = run_store(store_name, settings, http_client, event_store, now)

    if not report.written:
        raise RefreshFailedError(f"{store_name} was not written: {report.errors}")

    summary = {
        "store": store_name,
        "events": len(report.events),
        "droppedEnded": len(report.dropped_ended),
        "droppedUnverified": report.dropped_unverified,
    }
    logger.info("Refresh finished: %s", summary)
    return summary
