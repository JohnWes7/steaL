import json
from datetime import UTC, date, datetime
from pathlib import Path

import pytest

from sale_events import epic
from sale_events.models import ExtractedEvent
from sale_events.refresh import refresh_epic
from sale_events.storage import LocalEventStore

FIXTURES = Path(__file__).parent / "fixtures"


def load_fixture(name: str) -> dict:
    text = (FIXTURES / name).read_text(encoding="utf-8")
    return json.loads(text)


@pytest.fixture
def q4_guide() -> epic.EpicGuide:
    guide = epic.parse_guide(load_fixture("epic_guide_2026_q4.json"))
    assert guide is not None
    return guide


@pytest.fixture
def q3_guide() -> epic.EpicGuide:
    guide = epic.parse_guide(load_fixture("epic_guide_2026_q3.json"))
    assert guide is not None
    return guide


def test_select_guide_topics_takes_newest_two_guides() -> None:
    topics = epic.select_guide_topics(load_fixture("epic_search_2026-11-03.json"))
    titles = [topic["title"] for topic in topics]
    # 「Mid-Campaign Discounts」这类不是季度指南的帖子被过滤掉。
    # Posts that are not quarterly guides, such as "Mid-Campaign Discounts", are filtered out.
    assert titles == [
        "2026 Q4 Promotional Guide || EGS Promotions",
        "2026 Q3 Promotional Guide || Epic Games Promotions",
    ]


def test_parse_guide_builds_topic_url(q4_guide: epic.EpicGuide) -> None:
    assert q4_guide.title == "2026 Q4 Promotional Guide || EGS Promotions"
    assert q4_guide.url == "https://forums.unrealengine.com/t/2026-q4-promotional-guide-egs-promotions/2746185"


def test_parse_guide_rejects_non_staff_posts() -> None:
    payload = load_fixture("epic_guide_2026_q4.json")
    payload["post_stream"]["posts"][0]["staff"] = False
    assert epic.parse_guide(payload) is None


def test_extract_text_keeps_title_names_and_dates(q4_guide: epic.EpicGuide) -> None:
    text = epic.extract_text(q4_guide)
    assert text.startswith("# 2026 Q4 Promotional Guide || EGS Promotions\n")
    assert "## Epic Savings: Nov" in text
    assert "November 12 - 26 | 14 Days" in text
    assert "December 10 - January 7 | 28 Days" in text


def test_extract_text_drops_image_and_markup(q4_guide: epic.EpicGuide) -> None:
    text = epic.extract_text(q4_guide)
    assert "1600×402" not in text
    assert "<" not in text
    assert len(text) < 3000


def test_extract_text_handles_table_layout(q3_guide: epic.EpicGuide) -> None:
    text = epic.extract_text(q3_guide)
    assert "Jul 16 - 30 | 14 Days" in text
    assert "Epic Savings: Sep - Oct" in text
    # 空表格行不留下「|  |」。
    # Empty table rows do not leave "|  |" behind.
    assert "|  |" not in text


def test_to_sale_event_uses_8am_pacific(q4_guide: epic.EpicGuide) -> None:
    # 12 月和 1 月都是太平洋标准时间（UTC-8），8 点 = 16:00Z。
    # December and January are both Pacific standard time (UTC-8): 8 AM = 16:00Z.
    extracted = ExtractedEvent(name="Holiday Sale", start_date=date(2026, 12, 10), end_date=date(2027, 1, 7))
    event = epic.to_sale_event(extracted, q4_guide)
    assert event.start == datetime(2026, 12, 10, 16, tzinfo=UTC)
    assert event.end == datetime(2027, 1, 7, 16, tzinfo=UTC)
    assert event.id == "epic-2026-12-10-holiday-sale"
    assert event.store == "epic"
    assert event.kind == "sale"
    assert event.source_url == q4_guide.url


def test_refresh_epic_writes_events_with_their_guide_url(
    tmp_path: Path, q4_guide: epic.EpicGuide, q3_guide: epic.EpicGuide
) -> None:
    answers = {
        q4_guide.title: epic.EpicExtraction(
            events=[
                ExtractedEvent(name="Epic Savings: Nov", start_date=date(2026, 11, 12), end_date=date(2026, 11, 26)),
                ExtractedEvent(name="Holiday Sale", start_date=date(2026, 12, 10), end_date=date(2027, 1, 7)),
            ]
        ),
        q3_guide.title: epic.EpicExtraction(
            events=[
                ExtractedEvent(name="Summer Sale", start_date=date(2026, 7, 16), end_date=date(2026, 7, 30)),
            ]
        ),
    }

    def fake_extract(system_prompt: str, page_text: str, result_type: type) -> epic.EpicExtraction:
        first_line = page_text.splitlines()[0]
        title = first_line.removeprefix("# ")
        return answers[title]

    store = LocalEventStore(tmp_path)
    now = datetime(2026, 11, 3, tzinfo=UTC)
    report = refresh_epic(lambda: [q4_guide, q3_guide], fake_extract, store, now)

    assert report.written, report.errors
    assert report.dropped_ended == ["epic-2026-07-16-summer-sale"]
    payload = json.loads((tmp_path / "sale-events" / "epic.json").read_text())
    ids = [event["id"] for event in payload["events"]]
    assert ids == ["epic-2026-11-12-epic-savings-nov", "epic-2026-12-10-holiday-sale"]
    source_urls = {event["sourceURL"] for event in payload["events"]}
    assert source_urls == {q4_guide.url}
