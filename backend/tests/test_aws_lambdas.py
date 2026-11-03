import json
from collections.abc import Iterator
from datetime import UTC, datetime
from typing import Any

import api_handler
import boto3
import pytest
import refresh_handler
from moto import mock_aws
from s3_store import S3EventStore

from sale_events.models import SaleEvent, SaleEventList
from sale_events.refresh import RefreshReport

BUCKET = "steal-test-bucket"
REGION = "ap-east-1"


@pytest.fixture
def s3_client(monkeypatch: pytest.MonkeyPatch) -> Iterator[Any]:
    # moto 要求有凭证和区域，给假的，确保不会碰到真 AWS。
    # moto needs credentials and a region; fake ones guarantee real AWS is never touched.
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "testing")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "testing")
    monkeypatch.setenv("AWS_DEFAULT_REGION", REGION)
    monkeypatch.delenv("AWS_PROFILE", raising=False)
    with mock_aws():
        client = boto3.client("s3", region_name=REGION)
        client.create_bucket(Bucket=BUCKET, CreateBucketConfiguration={"LocationConstraint": REGION})
        yield client


def make_event_list(event_id: str, end: datetime) -> SaleEventList:
    event = SaleEvent(
        id=event_id,
        store="steam",
        kind="seasonalSale",
        name="Winter Sale 2026",
        start=datetime(2026, 12, 17, 18, tzinfo=UTC),
        end=end,
        source_url="https://example.com",
    )
    return SaleEventList(refreshed_at=datetime(2026, 11, 4, tzinfo=UTC), events=[event])


# MARK: - S3EventStore


def test_s3_store_round_trips_event_list(s3_client: Any) -> None:
    store = S3EventStore(s3_client, BUCKET)
    assert store.read("steam") is None

    event_list = make_event_list("steam-winter", datetime(2027, 1, 4, 18, tzinfo=UTC))
    store.write("steam", event_list)

    assert store.read("steam") == event_list
    stored = s3_client.get_object(Bucket=BUCKET, Key="sale-events/steam.json")
    assert stored["ContentType"] == "application/json"
    # 文件内容就是接口格式（camelCase），和本地 .data/ 一样。
    # The file content is the API format (camelCase), the same as the local .data/.
    payload = json.loads(stored["Body"].read())
    assert payload["events"][0]["sourceURL"] == "https://example.com"


def test_s3_store_writes_rejected_under_its_own_prefix(s3_client: Any) -> None:
    store = S3EventStore(s3_client, BUCKET)
    store.write_rejected("gog", datetime(2026, 11, 4, 1, 10, tzinfo=UTC), {"errors": ["bad quote"]})
    listing = s3_client.list_objects_v2(Bucket=BUCKET, Prefix="sale-events/rejected/")
    keys = [item["Key"] for item in listing["Contents"]]
    assert keys == ["sale-events/rejected/gog-20261104T011000Z.json"]


# MARK: - refresh Lambda


@pytest.fixture
def refresh_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setenv("BUCKET_NAME", BUCKET)


def test_refresh_handler_runs_only_the_named_store(
    s3_client: Any, refresh_env: None, monkeypatch: pytest.MonkeyPatch
) -> None:
    calls = []

    def fake_run_store(store_name: str, settings: Any, http_client: Any, event_store: Any, now: datetime) -> RefreshReport:
        calls.append((store_name, settings.api_key, event_store.bucket))
        return RefreshReport(written=True, dropped_unverified=["Mega Sale: quote is not on the source page"])

    monkeypatch.setattr(refresh_handler, "run_store", fake_run_store)
    summary = refresh_handler.handler({"store": "gog"}, None)

    assert calls == [("gog", "test-key", BUCKET)]
    assert summary["store"] == "gog"
    assert summary["droppedUnverified"] == ["Mega Sale: quote is not on the source page"]


def test_refresh_handler_raises_when_store_was_not_written(
    s3_client: Any, refresh_env: None, monkeypatch: pytest.MonkeyPatch
) -> None:
    def failing_run_store(*arguments: Any) -> RefreshReport:
        return RefreshReport(written=False, errors=["no upcoming events left"])

    monkeypatch.setattr(refresh_handler, "run_store", failing_run_store)
    with pytest.raises(refresh_handler.RefreshFailedError, match="no upcoming events left"):
        refresh_handler.handler({"store": "steam"}, None)


def test_refresh_handler_rejects_unknown_store(refresh_env: None) -> None:
    with pytest.raises(ValueError):
        refresh_handler.handler({"store": "humble"}, None)


# MARK: - API Lambda


@pytest.fixture
def api_env(s3_client: Any, monkeypatch: pytest.MonkeyPatch) -> Any:
    monkeypatch.setenv("BUCKET_NAME", BUCKET)
    monkeypatch.setattr(api_handler, "get_s3_client", lambda: s3_client)
    return s3_client


def test_api_handler_merges_store_files_and_skips_rejected(api_env: Any) -> None:
    store = S3EventStore(api_env, BUCKET)
    store.write("steam", make_event_list("steam-winter", datetime(2099, 1, 4, 18, tzinfo=UTC)))
    store.write_rejected("steam", datetime(2026, 11, 4, tzinfo=UTC), {"errors": ["x"]})

    response = api_handler.handler({}, None)

    assert response["statusCode"] == 200
    assert response["headers"]["Cache-Control"] == "public, max-age=3600"
    assert response["isBase64Encoded"] is False
    body = json.loads(response["body"])
    assert [event["id"] for event in body["events"]] == ["steam-winter"]


def test_api_handler_is_503_before_first_refresh(api_env: Any) -> None:
    response = api_handler.handler({}, None)
    assert response["statusCode"] == 503
    assert response["headers"]["Content-Type"] == "application/problem+json"


def test_api_handler_is_503_when_bucket_is_unreadable(api_env: Any, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("BUCKET_NAME", "missing-bucket")
    response = api_handler.handler({}, None)
    assert response["statusCode"] == 503
