from datetime import UTC, date, datetime

import pytest

from sale_events import steam
from sale_events.models import ExtractedEvent


def test_extract_text_keeps_all_sections(page_text: str) -> None:
    assert "## Seasonal Sales" in page_text
    assert "## Themed Sale Events (Fests)" in page_text
    assert "## Next Fest" in page_text
    assert "## 2027 Fests" in page_text


def test_extract_text_keeps_dates_and_names(page_text: str) -> None:
    assert "## Winter Sale 2026 | 17 December, 2026 - 4 January, 2027" in page_text
    assert "| Oct 26 - Nov 2 | Steam Scream V |" in page_text
    assert "## Next Fest | June 14 - 21, 2027" in page_text


def test_extract_text_drops_page_chrome(page_text: str) -> None:
    assert "Steamworks is the set of tools" not in page_text
    assert "<" not in page_text
    assert len(page_text) < 8000


def test_extract_text_keeps_spaces_around_inline_tags(page_text: str) -> None:
    assert "going fast." in page_text


def test_extract_text_fails_when_layout_changes() -> None:
    with pytest.raises(steam.SteamPageError):
        steam.extract_text("<html><body><p>new layout</p></body></html>")


def test_to_sale_event_uses_10am_pacific_across_dst_end() -> None:
    # 2026-11-01 太平洋时间结束夏令时：开始 UTC-7，结束 UTC-8。
    # DST ends on 2026-11-01 in the Pacific zone: start is UTC-7, end is UTC-8.
    extracted = ExtractedEvent(
        section="themedFest",
        name="Steam Scream V",
        start_date=date(2026, 10, 26),
        end_date=date(2026, 11, 2),
    )
    event = steam.to_sale_event(extracted)
    assert event.start == datetime(2026, 10, 26, 17, tzinfo=UTC)
    assert event.end == datetime(2026, 11, 2, 18, tzinfo=UTC)
    assert event.id == "steam-2026-10-26-steam-scream-v"
    assert event.kind == "themedFest"
    assert event.source_url == steam.SOURCE_URL


def test_to_sale_event_uses_10am_pacific_across_dst_start() -> None:
    # 2027-03-14 进入夏令时：开始 UTC-8，结束 UTC-7。
    # DST starts on 2027-03-14: start is UTC-8, end is UTC-7.
    extracted = ExtractedEvent(
        section="themedFest",
        name="Rhythm Fest",
        start_date=date(2027, 3, 8),
        end_date=date(2027, 3, 15),
    )
    event = steam.to_sale_event(extracted)
    assert event.start == datetime(2027, 3, 8, 18, tzinfo=UTC)
    assert event.end == datetime(2027, 3, 15, 17, tzinfo=UTC)
