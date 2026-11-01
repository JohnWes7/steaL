from datetime import UTC, datetime, timedelta

from sale_events import steam
from sale_events.models import ExtractionResult, SaleEvent
from sale_events.validation import validate_events

from .conftest import FIXTURE_NOW


def make_events(extraction: ExtractionResult) -> list[SaleEvent]:
    return [steam.to_sale_event(extracted) for extracted in extraction.events]


def test_expected_extraction_passes(expected_extraction: ExtractionResult, page_text: str) -> None:
    events = make_events(expected_extraction)
    outcome = validate_events(events, page_text, previous_count=None, now=FIXTURE_NOW)
    assert outcome.passed, outcome.errors
    assert len(outcome.kept) == 18


def test_ended_events_are_dropped_not_failed(expected_extraction: ExtractionResult, page_text: str) -> None:
    events = make_events(expected_extraction)
    after_scream = datetime(2026, 11, 3, tzinfo=UTC)
    outcome = validate_events(events, page_text, previous_count=None, now=after_scream)
    assert outcome.passed
    assert outcome.dropped_ended == ["steam-2026-10-26-steam-scream-v"]


def test_invented_name_fails(expected_extraction: ExtractionResult, page_text: str) -> None:
    events = make_events(expected_extraction)
    invented = events[0].model_copy(update={"name": "Mega Steam Party", "id": "steam-x"})
    outcome = validate_events([*events, invented], page_text, previous_count=None, now=FIXTURE_NOW)
    assert not outcome.passed
    assert any("not on the page" in error for error in outcome.errors)


def test_end_before_start_fails(expected_extraction: ExtractionResult, page_text: str) -> None:
    events = make_events(expected_extraction)
    first = events[0]
    reversed_event = first.model_copy(update={"end": first.start - timedelta(days=1)})
    outcome = validate_events([reversed_event], page_text, previous_count=None, now=FIXTURE_NOW)
    assert any("end is not after start" in error for error in outcome.errors)


def test_too_long_fails(expected_extraction: ExtractionResult, page_text: str) -> None:
    # 年份补错的典型样子：跨年的活动被算成一整年。
    # A typical wrong-year mistake: an event spanning New Year becomes a whole year long.
    events = make_events(expected_extraction)
    first = events[0]
    year_long = first.model_copy(update={"end": first.start + timedelta(days=365)})
    outcome = validate_events([year_long], page_text, previous_count=None, now=FIXTURE_NOW)
    assert any("more than 31" in error for error in outcome.errors)


def test_duplicate_id_fails(expected_extraction: ExtractionResult, page_text: str) -> None:
    events = make_events(expected_extraction)
    outcome = validate_events([*events, events[0]], page_text, previous_count=None, now=FIXTURE_NOW)
    assert any("duplicate id" in error for error in outcome.errors)


def test_sudden_drop_fails(expected_extraction: ExtractionResult, page_text: str) -> None:
    events = make_events(expected_extraction)
    outcome = validate_events(events[:5], page_text, previous_count=18, now=FIXTURE_NOW)
    assert any("fewer than half" in error for error in outcome.errors)


def test_empty_fails(page_text: str) -> None:
    outcome = validate_events([], page_text, previous_count=None, now=FIXTURE_NOW)
    assert not outcome.passed
