import json
from datetime import UTC, date, datetime, time
from pathlib import Path

import pytest

from sale_events import itchio
from sale_events.refresh import refresh_itchio
from sale_events.storage import LocalEventStore

FIXTURES = Path(__file__).parent / "fixtures"
NOW = datetime(2026, 11, 3, tzinfo=UTC)


def read_fixture(name: str) -> str:
    return (FIXTURES / name).read_text(encoding="utf-8")


def make_post(fixture_name: str, title: str, url: str, published: date) -> itchio.ItchioPost:
    body_html = itchio.parse_post_body(read_fixture(fixture_name))
    return itchio.ItchioPost(title=title, url=url, published=published, html=body_html)


@pytest.fixture
def halloween_post() -> itchio.ItchioPost:
    return make_post(
        "itchio_post_halloween_2026.html",
        "The itch.io Halloween Sale 2026 is live!",
        "https://itch.io/blog/1732743/the-itchio-halloween-sale-2026-is-live",
        date(2026, 10, 27),
    )


def test_parse_feed_reads_items() -> None:
    entries = itchio.parse_feed(read_fixture("itchio_blog_2026-11-03.rss"))
    assert entries[0].title == "The itch.io Halloween Sale 2026 is live!"
    assert entries[0].published_at == datetime(2026, 10, 27, 9, 54, 6, tzinfo=UTC)


def test_select_recent_entries_keeps_last_45_days_newest_first() -> None:
    entries = itchio.parse_feed(read_fixture("itchio_blog_2026-11-03.rss"))
    recent = itchio.select_recent_entries(entries, NOW)
    titles = [entry.title for entry in recent]
    # 10-06 的 itch app 更新也在窗口里（交给 AI 判断），6 月的 Summer Sale 和去年的 Black Friday 不在。
    # The 10-06 itch app update is in the window (the AI decides); June's Summer Sale and last year's Black Friday are not.
    assert titles == ["The itch.io Halloween Sale 2026 is live!", "itch app Collections and Steam Shortcuts Proton Support"]


def test_parse_post_body_fails_when_layout_changes() -> None:
    with pytest.raises(itchio.ItchioPageError):
        itchio.parse_post_body("<html><body><p>new layout</p></body></html>")


def test_extract_text_starts_with_title_and_published_date(halloween_post: itchio.ItchioPost) -> None:
    text = itchio.extract_text(halloween_post)
    assert text.startswith("# The itch.io Halloween Sale 2026 is live!\nPublished: 2026-10-27\n")
    assert "October 25th at 10:00 AM PT and runs until November 4th at 10:00 AM PT" in text
    assert "<" not in text


def test_to_sale_event_uses_stated_pacific_times(halloween_post: itchio.ItchioPost) -> None:
    # 10-25 是夏令时（UTC-7），11-04 已回到标准时间（UTC-8）。
    # October 25 is daylight time (UTC-7); November 4 is back on standard time (UTC-8).
    extracted = itchio.ItchioExtractedEvent(
        name="itch.io Halloween Sale 2026",
        start_date=date(2026, 10, 25),
        end_date=date(2026, 11, 4),
        start_time=time(10),
        end_time=time(10),
    )
    event = itchio.to_sale_event(extracted, halloween_post)
    assert event.start == datetime(2026, 10, 25, 17, tzinfo=UTC)
    assert event.end == datetime(2026, 11, 4, 18, tzinfo=UTC)
    assert event.id == "itchio-2026-10-25-itch-io-halloween-sale-2026"
    assert event.store == "itchio"
    assert event.kind == "sale"
    assert event.source_url == halloween_post.url


def test_to_sale_event_defaults_to_10am_pacific(halloween_post: itchio.ItchioPost) -> None:
    extracted = itchio.ItchioExtractedEvent(
        name="itch.io Summer Sale", start_date=date(2026, 6, 25), end_date=date(2026, 7, 6)
    )
    event = itchio.to_sale_event(extracted, halloween_post)
    assert event.start == datetime(2026, 6, 25, 17, tzinfo=UTC)
    assert event.end == datetime(2026, 7, 6, 17, tzinfo=UTC)


def test_refresh_itchio_writes_empty_list_when_no_sale(tmp_path: Path) -> None:
    def no_sale(system_prompt: str, page_text: str, result_type: type) -> itchio.ItchioExtraction:
        return itchio.ItchioExtraction(events=[])

    store = LocalEventStore(tmp_path)
    report = refresh_itchio(lambda: [], no_sale, store, NOW)
    assert report.written, report.errors
    payload = json.loads((tmp_path / "sale-events" / "itchio.json").read_text())
    assert payload["events"] == []


def test_refresh_itchio_newer_post_wins_for_same_sale(tmp_path: Path, halloween_post: itchio.ItchioPost) -> None:
    older_post = make_post(
        "itchio_post_halloween_2026.html",
        "The itch.io Halloween Sale 2026 starts soon",
        "https://itch.io/blog/1/older",
        date(2026, 10, 20),
    )
    answers = {
        halloween_post.url: itchio.ItchioExtraction(
            events=[
                itchio.ItchioExtractedEvent(
                    name="itch.io Halloween Sale 2026",
                    start_date=date(2026, 10, 25),
                    end_date=date(2026, 11, 6),
                    start_time=time(10),
                    end_time=time(10),
                )
            ]
        ),
        older_post.url: itchio.ItchioExtraction(
            events=[
                itchio.ItchioExtractedEvent(
                    name="itch.io Halloween Sale 2026",
                    start_date=date(2026, 10, 25),
                    end_date=date(2026, 11, 4),
                )
            ]
        ),
    }
    posts_by_title = {halloween_post.title: halloween_post.url, older_post.title: older_post.url}

    def fake_extract(system_prompt: str, page_text: str, result_type: type) -> itchio.ItchioExtraction:
        first_line = page_text.splitlines()[0]
        title = first_line.removeprefix("# ")
        url = posts_by_title[title]
        return answers[url]

    store = LocalEventStore(tmp_path)
    report = refresh_itchio(lambda: [halloween_post, older_post], fake_extract, store, NOW)

    assert report.written, report.errors
    assert len(report.events) == 1
    # 新帖子（延长到 11-06）为准。
    # The newer post (extended to November 6) wins.
    assert report.events[0].end == datetime(2026, 11, 6, 18, tzinfo=UTC)
    assert report.events[0].source_url == halloween_post.url
