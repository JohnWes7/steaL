import json
from datetime import UTC, datetime

import api_core

NOW = datetime(2026, 11, 3, tzinfo=UTC)


def make_event(event_id: str, store: str, start: str, end: str) -> dict:
    return {
        "id": event_id,
        "store": store,
        "kind": "themedFest",
        "name": event_id,
        "start": start,
        "end": end,
        "sourceURL": "https://example.com",
    }


def make_file(refreshed_at: str, events: list[dict]) -> str:
    return json.dumps({"refreshedAt": refreshed_at, "events": events})


STEAM_FILE = make_file(
    "2026-11-01T10:36:27.639446Z",
    [
        make_event("steam-ended", "steam", "2026-10-26T17:00:00Z", "2026-11-02T18:00:00Z"),
        make_event("steam-winter", "steam", "2026-12-17T18:00:00Z", "2027-01-04T18:00:00Z"),
    ],
)
GOG_FILE = make_file(
    "2026-10-30T00:00:00Z",
    [make_event("gog-autumn", "gog", "2026-11-20T15:00:00Z", "2026-11-27T15:00:00Z")],
)


def test_merge_drops_ended_and_sorts_across_stores() -> None:
    merged = api_core.merge_store_files([STEAM_FILE, GOG_FILE], NOW)
    ids = [event["id"] for event in merged["events"]]
    assert ids == ["gog-autumn", "steam-winter"]


def test_merge_uses_earliest_refreshed_at() -> None:
    merged = api_core.merge_store_files([STEAM_FILE, GOG_FILE], NOW)
    assert merged["refreshedAt"] == "2026-10-30T00:00:00Z"


def test_ok_response_has_cache_header() -> None:
    response = api_core.list_sale_events(lambda: [STEAM_FILE], NOW)
    assert response.status == 200
    assert response.headers["Content-Type"] == "application/json"
    assert response.headers["Cache-Control"] == api_core.CACHE_CONTROL
    body = json.loads(response.body)
    assert [event["id"] for event in body["events"]] == ["steam-winter"]


def test_all_ended_is_ok_and_empty() -> None:
    response = api_core.list_sale_events(lambda: [STEAM_FILE], datetime(2027, 2, 1, tzinfo=UTC))
    assert response.status == 200
    assert json.loads(response.body)["events"] == []


def test_no_files_is_503() -> None:
    response = api_core.list_sale_events(lambda: [], NOW)
    assert response.status == 503
    assert response.headers["Content-Type"] == "application/problem+json"
    assert json.loads(response.body)["status"] == 503


def test_unreadable_storage_is_503() -> None:
    def broken_reader() -> list[str]:
        raise PermissionError("denied")

    response = api_core.list_sale_events(broken_reader, NOW)
    assert response.status == 503


def test_corrupted_file_is_500() -> None:
    response = api_core.list_sale_events(lambda: ["{not json"], NOW)
    assert response.status == 500
    assert response.headers["Content-Type"] == "application/problem+json"
