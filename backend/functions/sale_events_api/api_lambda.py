"""AWS 专用：API Lambda 的入口。从 S3 读各商店的文件，交给与云无关的 `func.list_sale_events`，转成 HTTP API 的响应。
AWS only: the API Lambda's entry point. Reads each store's file from S3, hands them to the cloud-neutral
`func.list_sale_events`, and converts the result to an HTTP API response.

API Gateway HTTP API 只把 `GET /v1/sale-events` 路由到这里，其他路径由网关直接返回 404。
API Gateway HTTP API only routes `GET /v1/sale-events` here; other paths get a 404 from the gateway itself.
"""

import functools
import os
from collections.abc import Callable
from datetime import UTC, datetime
from typing import Any

import boto3
import func
from botocore.exceptions import BotoCoreError, ClientError

PREFIX = "sale-events/"


@functools.cache
def get_s3_client() -> Any:
    """返回复用的 S3 客户端。
    Returns the reused S3 client.

    放在 handler 外面缓存，热启动的调用之间复用连接（AWS 的 Lambda 最佳实践）；测试时替换掉这个函数。
    Cached outside the handler so warm invocations reuse the connection (an AWS Lambda best practice); tests replace it.
    """
    return boto3.client("s3")


def make_reader(client: Any, bucket: str) -> Callable[[], list[str]]:
    """返回一个读取 `sale-events/*.json`（不含 `rejected/`）的函数。
    Returns a function that reads `sale-events/*.json` (excluding `rejected/`).

    读不到存储时抛 `OSError`，`func.list_sale_events` 会把它转成 503。
    Unreadable storage raises `OSError`, which `func.list_sale_events` turns into a 503.
    """

    def read_store_files() -> list[str]:
        try:
            # Delimiter "/" 只列出这一层的文件，`rejected/` 下的不会出现。
            # Delimiter "/" lists only this level, so files under `rejected/` are left out.
            listing = client.list_objects_v2(Bucket=bucket, Prefix=PREFIX, Delimiter="/")
            objects = listing.get("Contents", [])
            keys = [item["Key"] for item in objects if item["Key"].endswith(".json")]
            texts = []
            for key in keys:
                response = client.get_object(Bucket=bucket, Key=key)
                body = response["Body"].read()
                texts.append(body.decode("utf-8"))
        except (BotoCoreError, ClientError) as error:
            raise OSError(f"reading s3://{bucket}/{PREFIX} failed") from error
        return texts

    return read_store_files


def to_http_api_response(response: func.Response) -> dict[str, Any]:
    """把与云无关的 `Response` 转成 HTTP API（payload 2.0）要的格式。
    Converts the cloud-neutral `Response` into the HTTP API (payload 2.0) format.
    """
    return {
        "statusCode": response.status,
        "headers": response.headers,
        "body": response.body,
        "isBase64Encoded": False,
    }


def handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Lambda 入口：返回合并后的活动列表。
    Lambda entry point: returns the merged event list.
    """
    reader = make_reader(get_s3_client(), os.environ["BUCKET_NAME"])
    now = datetime.now(UTC)
    response = func.list_sale_events(reader, now)
    return to_http_api_response(response)
