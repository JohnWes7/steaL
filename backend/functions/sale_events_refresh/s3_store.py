"""AWS 专用：把活动数据存进 S3 的 `EventStore` 实现，布局和本地 `.data/` 一样。
AWS only: the `EventStore` implementation that keeps event data in S3, with the same layout as the local `.data/`.

换云时只换这个文件（和入口），`sale_events` 包本身不 import boto3。
Switching clouds replaces only this file (and the entry point); the `sale_events` package never imports boto3.
"""

import json
from datetime import datetime
from typing import Any

from sale_events.models import SaleEventList

PREFIX = "sale-events"


class S3EventStore:
    """读写 S3 的存储：`sale-events/{store}.json` 与 `sale-events/rejected/{store}-{时间}.json`。
    S3-backed storage: `sale-events/{store}.json` and `sale-events/rejected/{store}-{time}.json`.

    - Parameters:
      - client: boto3 的 S3 客户端，由调用方创建（测试时传 moto 的）/ A boto3 S3 client created by the caller (moto in tests).
      - bucket: bucket 名 / The bucket name.
    """

    def __init__(self, client: Any, bucket: str) -> None:
        self.client = client
        self.bucket = bucket

    def read(self, store: str) -> SaleEventList | None:
        key = f"{PREFIX}/{store}.json"
        try:
            response = self.client.get_object(Bucket=self.bucket, Key=key)
        except self.client.exceptions.NoSuchKey:
            return None
        body = response["Body"].read()
        text = body.decode("utf-8")
        return SaleEventList.model_validate_json(text)

    def write(self, store: str, event_list: SaleEventList) -> None:
        # S3 的 PUT 是整体替换，读的一方不会读到写了一半的对象。
        # An S3 PUT replaces the object as a whole, so readers never see a half-written object.
        key = f"{PREFIX}/{store}.json"
        text = event_list.model_dump_json(by_alias=True, indent=2)
        body = (text + "\n").encode("utf-8")
        self.client.put_object(Bucket=self.bucket, Key=key, Body=body, ContentType="application/json")

    def write_rejected(self, store: str, rejected_at: datetime, payload: dict[str, Any]) -> None:
        timestamp = rejected_at.strftime("%Y%m%dT%H%M%SZ")
        key = f"{PREFIX}/rejected/{store}-{timestamp}.json"
        text = json.dumps(payload, ensure_ascii=False, indent=2, default=str)
        body = (text + "\n").encode("utf-8")
        self.client.put_object(Bucket=self.bucket, Key=key, Body=body, ContentType="application/json")
