import json
from datetime import UTC, date, datetime, time
from pathlib import Path
from types import SimpleNamespace
from typing import Any

import pytest

from sale_events import agent
from sale_events.models import SaleEvent, SaleEventList
from sale_events.refresh import refresh_with_agent
from sale_events.storage import LocalEventStore

NOW = datetime(2026, 11, 3, tzinfo=UTC)
PAGE_URL = "https://www.gog.com/en/news/gog_turns_18_celebrate_with_our_anniversary_sale"
PAGE_CONTENT = (
    "# GOG turns 18! Celebrate with our Anniversary Sale\n\n"
    "Our Anniversary Sale runs until November 5 at 9 AM CET, with discounts on a wide selection of games."
)

SOURCE = agent.AgentSource(
    store="gog",
    display_name="GOG",
    kind="sale",
    task="store-wide sales on GOG.com.",
    allowed_domains=("gog.com",),
    default_time=time(8),
    default_zone=UTC,
    max_rounds=4,
    max_pages=2,
)


def tool_call(call_id: str, name: str, arguments: dict[str, Any]) -> SimpleNamespace:
    function = SimpleNamespace(name=name, arguments=json.dumps(arguments))
    return SimpleNamespace(id=call_id, type="function", function=function)


def assistant(*calls: SimpleNamespace) -> SimpleNamespace:
    return SimpleNamespace(content=None, tool_calls=list(calls))


SUBMITTED = {
    "name": "Anniversary Sale",
    "startDate": "2026-10-28",
    "endDate": "2026-11-05",
    "startTime": None,
    "endTime": "09:00",
    "timeZone": "CET",
    "sourceURL": PAGE_URL,
    "quote": "Our Anniversary Sale runs until November 5 at 9 AM CET, with discounts on a wide selection of games.",
}


class FakeChat:
    """按顺序返回预设的 assistant 消息，并记下每轮给了哪些工具。
    Returns preset assistant messages in order and records which tools each round offered.
    """

    def __init__(self, replies: list[SimpleNamespace]) -> None:
        self.replies = replies
        self.offered_tool_names: list[list[str]] = []

    def complete(self, messages: list[dict[str, Any]], tools: list[dict[str, Any]]) -> SimpleNamespace:
        names = [tool["function"]["name"] for tool in tools]
        self.offered_tool_names.append(names)
        round_index = len(self.offered_tool_names) - 1
        return self.replies[round_index]


class FakeWebTools:
    def __init__(self) -> None:
        self.fetched: list[str] = []

    def search(self, query: str) -> list[dict[str, Any]]:
        return [{"title": "GOG turns 18", "url": PAGE_URL, "date": "2026-10-28", "summary": "Anniversary Sale"}]

    def fetch(self, url: str) -> str:
        self.fetched.append(url)
        if url == PAGE_URL:
            return PAGE_CONTENT
        return f"# Other page {url}"


def test_run_agent_searches_fetches_and_submits() -> None:
    chat = FakeChat([
        assistant(tool_call("1", "web_search", {"query": "site:gog.com news sale"})),
        assistant(tool_call("2", "fetch_url", {"url": PAGE_URL})),
        assistant(tool_call("3", "submit_events", {"events": [SUBMITTED]})),
    ])
    run = agent.run_agent(SOURCE, chat, FakeWebTools(), NOW)
    assert len(run.submission.events) == 1
    assert run.pages == {PAGE_URL: PAGE_CONTENT}
    assert run.log == [
        "round 1: search 'site:gog.com news sale'",
        f"round 2: fetch {PAGE_URL}",
        "round 3: submitted 1 events",
    ]


def test_last_round_only_offers_submit() -> None:
    chat = FakeChat([
        assistant(tool_call("1", "web_search", {"query": "a"})),
        assistant(tool_call("2", "web_search", {"query": "b"})),
        assistant(tool_call("3", "web_search", {"query": "c"})),
        assistant(tool_call("4", "submit_events", {"events": []})),
    ])
    run = agent.run_agent(SOURCE, chat, FakeWebTools(), NOW)
    assert run.submission.events == []
    assert chat.offered_tool_names[-1] == ["submit_events"]
    assert chat.offered_tool_names[0] == ["web_search", "fetch_url", "submit_events"]


def test_run_agent_fails_without_submission() -> None:
    no_call = SimpleNamespace(content="I think there is a sale.", tool_calls=None)
    chat = FakeChat([no_call, no_call, no_call, no_call])
    with pytest.raises(agent.AgentError):
        agent.run_agent(SOURCE, chat, FakeWebTools(), NOW)


def test_page_limit_refuses_extra_fetches() -> None:
    tools = FakeWebTools()
    chat = FakeChat([
        assistant(
            tool_call("1", "fetch_url", {"url": "https://www.gog.com/a"}),
            tool_call("2", "fetch_url", {"url": "https://www.gog.com/b"}),
            tool_call("3", "fetch_url", {"url": "https://www.gog.com/c"}),
        ),
        assistant(tool_call("4", "submit_events", {"events": []})),
    ])
    run = agent.run_agent(SOURCE, chat, tools, NOW)
    assert tools.fetched == ["https://www.gog.com/a", "https://www.gog.com/b"]
    assert "round 1: page limit reached, refused https://www.gog.com/c" in run.log


def make_run(**overrides: Any) -> agent.AgentRun:
    event_fields = {**SUBMITTED, **overrides}
    submission = agent.AgentSubmission.model_validate({"events": [event_fields]})
    return agent.AgentRun(submission=submission, pages={PAGE_URL: PAGE_CONTENT})


def test_verify_source_accepts_quote_on_opened_official_page() -> None:
    run = make_run()
    assert agent.verify_source(run.submission.events[0], run, SOURCE) is None


@pytest.mark.parametrize(
    ("overrides", "reason_part"),
    [
        ({"sourceURL": "https://www.gamewatcher.com/gog-sales"}, "not on an allowed domain"),
        ({"sourceURL": "https://www.gog.com/en/news/never_opened"}, "was not opened"),
        ({"quote": "Our Anniversary Sale runs until November 9."}, "not on the source page"),
        ({"endDate": "2026-11-06"}, "does not contain the end day 6"),
    ],
)
def test_verify_source_rejects(overrides: dict[str, Any], reason_part: str) -> None:
    run = make_run(**overrides)
    reason = agent.verify_source(run.submission.events[0], run, SOURCE)
    assert reason is not None
    assert reason_part in reason


def test_is_allowed_url_rejects_lookalike_domains() -> None:
    assert agent.is_allowed_url("https://www.gog.com/en/news/x", ("gog.com",))
    assert not agent.is_allowed_url("https://gog.com.evil.example/x", ("gog.com",))
    assert not agent.is_allowed_url("https://notgog.com/x", ("gog.com",))


def test_to_sale_event_converts_stated_time_and_defaults() -> None:
    run = make_run()
    event = agent.to_sale_event(run.submission.events[0], SOURCE)
    # 结束 9:00 CET = 8:00 UTC；开始没写时刻，用默认 8:00 UTC。
    # The end at 9:00 CET is 8:00 UTC; the start has no time, so the 8:00 UTC default applies.
    assert event.end == datetime(2026, 11, 5, 8, tzinfo=UTC)
    assert event.start == datetime(2026, 10, 28, 8, tzinfo=UTC)
    assert event.id == "gog-2026-10-28-anniversary-sale"
    assert event.source_url == PAGE_URL


def test_pacific_label_follows_daylight_saving() -> None:
    run = make_run(endTime="10:00", timeZone="PT", endDate="2026-07-06")
    event = agent.to_sale_event(run.submission.events[0], SOURCE)
    assert event.end == datetime(2026, 7, 6, 17, tzinfo=UTC)


def test_unknown_time_zone_label_is_rejected() -> None:
    with pytest.raises(ValueError):
        make_run(timeZone="AEST")


def test_refresh_with_agent_upserts_and_drops_unverified(tmp_path: Path) -> None:
    store = LocalEventStore(tmp_path)
    kept_previous = SaleEvent(
        id="gog-2026-11-20-black-friday",
        store="gog",
        kind="sale",
        name="Black Friday",
        start=datetime(2026, 11, 20, 8, tzinfo=UTC),
        end=datetime(2026, 11, 30, 8, tzinfo=UTC),
        source_url="https://www.gog.com/en/news/bf",
    )
    ended_previous = kept_previous.model_copy(
        update={"id": "gog-2026-09-24-autumn", "end": datetime(2026, 10, 7, 7, tzinfo=UTC)}
    )
    store.write("gog", SaleEventList(refreshed_at=datetime(2026, 10, 1, tzinfo=UTC), events=[ended_previous, kept_previous]))

    invented = {**SUBMITTED, "name": "Mega Sale", "quote": "Mega Sale until November 5."}
    submission = agent.AgentSubmission.model_validate({"events": [SUBMITTED, invented]})
    run = agent.AgentRun(submission=submission, pages={PAGE_URL: PAGE_CONTENT}, log=["round 1: fetch"])

    report = refresh_with_agent(SOURCE, lambda: run, store, NOW)

    assert report.written
    ids = [event.id for event in report.events]
    assert ids == ["gog-2026-10-28-anniversary-sale", "gog-2026-11-20-black-friday"]
    assert report.dropped_unverified == ["Mega Sale: quote is not on the source page"]
    rejected_files = list((tmp_path / "sale-events" / "rejected").glob("gog-*.json"))
    assert len(rejected_files) == 1


def test_refresh_with_agent_keeps_old_data_when_agent_fails(tmp_path: Path) -> None:
    def failing_agent() -> agent.AgentRun:
        raise agent.AgentError("no submission after 4 rounds", ["round 1: search 'x'"])

    store = LocalEventStore(tmp_path)
    report = refresh_with_agent(SOURCE, failing_agent, store, NOW)
    assert not report.written
    assert not (tmp_path / "sale-events" / "gog.json").exists()
    rejected = list((tmp_path / "sale-events" / "rejected").glob("gog-*.json"))
    assert json.loads(rejected[0].read_text())["log"] == ["round 1: search 'x'"]


def test_refresh_with_agent_replaces_renamed_event_with_same_end_day(tmp_path: Path) -> None:
    store = LocalEventStore(tmp_path)
    # 上一次 AI 把名字抄成了文章标题，id 不同，但结束日期相同。
    # Last time the AI copied the article headline as the name: a different id, but the same end day.
    headline_named = SaleEvent(
        id="gog-2026-10-27-gog-turns-18-celebrate-with-our-anniversary-sale",
        store="gog",
        kind="sale",
        name="GOG turns 18! Celebrate with our Anniversary Sale",
        start=datetime(2026, 10, 27, 8, tzinfo=UTC),
        end=datetime(2026, 11, 5, 8, tzinfo=UTC),
        source_url=PAGE_URL,
    )
    store.write("gog", SaleEventList(refreshed_at=datetime(2026, 11, 2, tzinfo=UTC), events=[headline_named]))

    run = make_run()
    report = refresh_with_agent(SOURCE, lambda: run, store, NOW)

    ids = [event.id for event in report.events]
    assert ids == ["gog-2026-10-28-anniversary-sale"]
