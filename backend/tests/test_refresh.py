import json
from pathlib import Path

from sale_events import steam
from sale_events.extraction import ExtractionError
from sale_events.refresh import refresh_steam
from sale_events.storage import LocalEventStore

from .conftest import FIXTURE_NOW


def test_refresh_writes_store_file(tmp_path: Path, page_html: str, expected_extraction: steam.SteamExtraction) -> None:
    store = LocalEventStore(tmp_path)
    report = refresh_steam(
        fetch_html=lambda: page_html,
        extract=lambda system_prompt, page_text, result_type: expected_extraction,
        store=store,
        now=FIXTURE_NOW,
    )
    assert report.written

    written_path = tmp_path / "sale-events" / "steam.json"
    payload = json.loads(written_path.read_text())
    assert payload["refreshedAt"] == "2026-11-01T00:00:00Z"
    first = payload["events"][0]
    # 接口约定：camelCase、sourceURL、UTC 带 Z、按开始时间排序。
    # API contract: camelCase, sourceURL, UTC with Z, sorted by start.
    assert first == {
        "id": "steam-2026-10-26-steam-scream-v",
        "store": "steam",
        "kind": "themedFest",
        "name": "Steam Scream V",
        "start": "2026-10-26T17:00:00Z",
        "end": "2026-11-02T18:00:00Z",
        "sourceURL": "https://partner.steamgames.com/doc/marketing/upcoming_events",
    }
    starts = [event["start"] for event in payload["events"]]
    assert starts == sorted(starts)


def test_refresh_keeps_old_data_when_validation_fails(
    tmp_path: Path, page_html: str, expected_extraction: steam.SteamExtraction
) -> None:
    store = LocalEventStore(tmp_path)
    refresh_steam(lambda: page_html, lambda system_prompt, page_text, result_type: expected_extraction, store, FIXTURE_NOW)
    written_path = tmp_path / "sale-events" / "steam.json"
    before = written_path.read_text()

    # 只剩 3 条，少于上一份的一半。
    # Only 3 events left, fewer than half of the previous run.
    shrunk = steam.SteamExtraction(events=expected_extraction.events[:3])
    report = refresh_steam(lambda: page_html, lambda system_prompt, page_text, result_type: shrunk, store, FIXTURE_NOW)

    assert not report.written
    assert written_path.read_text() == before
    rejected_files = list((tmp_path / "sale-events" / "rejected").glob("steam-*.json"))
    assert len(rejected_files) == 1
    rejected = json.loads(rejected_files[0].read_text())
    assert rejected["aiOutput"]["events"][0]["startDate"] == "2026-12-17"


def test_refresh_saves_raw_output_when_ai_returns_garbage(tmp_path: Path, page_html: str) -> None:
    def broken_extract(system_prompt: str, page_text: str, result_type: type) -> steam.SteamExtraction:
        raise ExtractionError("not json", "Sure! Here are the events")

    store = LocalEventStore(tmp_path)
    report = refresh_steam(lambda: page_html, broken_extract, store, FIXTURE_NOW)

    assert not report.written
    assert not (tmp_path / "sale-events" / "steam.json").exists()
    rejected_files = list((tmp_path / "sale-events" / "rejected").glob("steam-*.json"))
    rejected = json.loads(rejected_files[0].read_text())
    assert rejected["rawOutput"] == "Sure! Here are the events"
