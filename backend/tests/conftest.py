"""测试共用的 fixture：2026-11-01 存下来的 Steamworks 页面与手工整理的正确答案。
Shared fixtures: the Steamworks page saved on 2026-11-01 and the hand-checked expected extraction.

页面改版时重新存一份 HTML，并照页面更新 `.expected.json`。
When the page layout changes, save a new HTML copy and update `.expected.json` from the page.
"""

from datetime import UTC, datetime
from pathlib import Path

import pytest

from sale_events import steam

FIXTURES = Path(__file__).parent / "fixtures"
PAGE_PATH = FIXTURES / "steamworks_upcoming_events_2026-11-01.html"
EXPECTED_PATH = FIXTURES / "steamworks_upcoming_events_2026-11-01.expected.json"

FIXTURE_NOW = datetime(2026, 11, 1, tzinfo=UTC)
"""存页面那天的 0 点（UTC），这时 Steam Scream V 还没结束。
Midnight UTC on the day the page was saved; Steam Scream V has not ended yet.
"""


@pytest.fixture
def page_html() -> str:
    return PAGE_PATH.read_text(encoding="utf-8")


@pytest.fixture
def page_text(page_html: str) -> str:
    return steam.extract_text(page_html)


@pytest.fixture
def expected_extraction() -> steam.SteamExtraction:
    text = EXPECTED_PATH.read_text(encoding="utf-8")
    return steam.SteamExtraction.model_validate_json(text)
