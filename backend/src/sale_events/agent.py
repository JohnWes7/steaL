"""通用的找活动 agent：让 DeepSeek 自己搜索、打开网页，最后提交带原文引文的活动，代码逐条核对。
A generic event-finding agent: DeepSeek searches and opens pages itself, then submits events with verbatim quotes,
and the code verifies each one.

给没有固定日程来源的商店用（如 GOG）。每个商店只需要一份 `AgentSource` 配置。
For stores without a fixed schedule source (such as GOG). Each store only needs one `AgentSource` config.
"""

import json
import logging
import re
from dataclasses import dataclass, field
from datetime import UTC, date, datetime, time, timedelta, timezone, tzinfo
from typing import Any, Literal, Protocol
from urllib.parse import urlsplit
from zoneinfo import ZoneInfo

import httpx
from openai import OpenAI
from pydantic import Field, ValidationError

from .extraction import DEFAULT_BASE_URL, DEFAULT_MODEL
from .models import CamelModel, ExtractedEvent, SaleEvent
from .normalize import collapse_spaces, local_moment_to_utc, make_event_id

logger = logging.getLogger(__name__)

SEARCH_RESULT_COUNT = 8
SEARCH_SUMMARY_CHARS = 600
PAGE_CHARS_FOR_MODEL = 30000
"""网页给模型看的最多字符数。官方页前面常有很长的 cookie 说明，太短会切掉正文；核对引文用的是完整内容。
The most characters of a page shown to the model. Official pages often open with long cookie notices, so a short
limit would cut the article; quotes are verified against the full content.
"""

TimeZoneLabel = Literal["UTC", "GMT", "CET", "CEST", "PT", "PST", "PDT", "ET", "EST", "EDT"]
"""AI 可以照抄的时区写法。缩写按字面意思换算（`CET` 就是 UTC+1），只有 `PT` / `ET` 跟着夏令时走。
The time zone labels the AI may copy. Abbreviations are taken literally (`CET` is UTC+1); only `PT` / `ET` follow
daylight saving time.
"""

TIME_ZONES: dict[str, tzinfo] = {
    "UTC": UTC,
    "GMT": UTC,
    "CET": timezone(timedelta(hours=1)),
    "CEST": timezone(timedelta(hours=2)),
    "PT": ZoneInfo("America/Los_Angeles"),
    "PST": timezone(timedelta(hours=-8)),
    "PDT": timezone(timedelta(hours=-7)),
    "ET": ZoneInfo("America/New_York"),
    "EST": timezone(timedelta(hours=-5)),
    "EDT": timezone(timedelta(hours=-4)),
}


@dataclass
class AgentSource:
    """一个由 agent 找活动的商店的配置。
    The config of a store whose events are found by the agent.
    """

    store: str
    display_name: str
    kind: str
    task: str
    """要找哪些活动、不要哪些（写给 AI 看的英文）/ Which events to find and which to skip (English, for the AI)."""
    allowed_domains: tuple[str, ...]
    """来源只认这些官方域名（含子域名）/ Only these official domains (and subdomains) are accepted as sources."""
    default_time: time
    default_zone: tzinfo
    """网页没写时刻时用的时刻和时区 / The time and zone used when a page gives no time."""
    horizon_days: int = 180
    max_rounds: int = 8
    max_pages: int = 6


class AgentSubmittedEvent(ExtractedEvent):
    """AI 提交的一条活动：日期、可选的时刻与时区、来源网址和原文引文。
    One event submitted by the AI: dates, optional times and time zone, source URL and a verbatim quote.
    """

    start_time: time | None = None
    end_time: time | None = None
    time_zone: TimeZoneLabel | None = None
    source_url: str = Field(alias="sourceURL")
    quote: str


class AgentSubmission(CamelModel):
    """`submit_events` 的参数。
    The arguments of `submit_events`.
    """

    events: list[AgentSubmittedEvent]


@dataclass
class AgentRun:
    """一次 agent 运行的结果：提交的活动、真正打开过的网页（地址 → 完整内容）、过程日志。
    The result of one agent run: submitted events, the pages actually opened (URL → full content), and a log.
    """

    submission: AgentSubmission
    pages: dict[str, str] = field(default_factory=dict)
    log: list[str] = field(default_factory=list)


class AgentError(Exception):
    """agent 用完轮数也没有提交结果。带着过程日志，方便排查。
    The agent ran out of rounds without submitting. Carries the log for debugging.
    """

    def __init__(self, message: str, log: list[str]) -> None:
        super().__init__(message)
        self.log = log


class WebToolError(Exception):
    """搜索或打开网页失败（会告诉 AI，让它换一个）。
    A search or page fetch failed (the AI is told so it can try something else).
    """


class WebTools(Protocol):
    """agent 用的两个网络工具，测试时换成假的。
    The two web tools the agent uses; tests pass fakes.
    """

    def search(self, query: str) -> list[dict[str, Any]]: ...

    def fetch(self, url: str) -> str: ...


class AgentChat(Protocol):
    """发一轮带工具的对话，返回 assistant 消息（有 `content` 与 `tool_calls`），测试时换成假的。
    Sends one chat round with tools and returns the assistant message (with `content` and `tool_calls`); tests pass fakes.
    """

    def complete(self, messages: list[dict[str, Any]], tools: list[dict[str, Any]]) -> Any: ...


# MARK: - DeepSeek 实现 / DeepSeek implementations


class DeepSeekWebTools:
    """用 DeepSeek 的 Search / Fetch 接口实现网络工具（和聊天同一个 key）。
    Web tools backed by DeepSeek's Search / Fetch endpoints (same key as chat).
    """

    def __init__(self, api_key: str, client: httpx.Client, base_url: str = DEFAULT_BASE_URL) -> None:
        self.headers = {"Authorization": f"Bearer {api_key}"}
        self.client = client
        self.base_url = base_url

    def search(self, query: str) -> list[dict[str, Any]]:
        payload = {"query": query, "count": SEARCH_RESULT_COUNT}
        response = self.client.post(f"{self.base_url}/v1/tools/search", headers=self.headers, json=payload, timeout=60)
        if response.status_code != 200:
            raise WebToolError(f"search failed with HTTP {response.status_code}")
        return response.json()["results"]

    def fetch(self, url: str) -> str:
        payload = {"url": url}
        response = self.client.post(f"{self.base_url}/v1/tools/fetch", headers=self.headers, json=payload, timeout=60)
        if response.status_code != 200:
            raise WebToolError(f"fetch failed with HTTP {response.status_code}")
        return response.json()["content"]


class DeepSeekChat:
    """用 DeepSeek Chat Completions 跑 tool calling（关掉思考模式，同官方示例）。
    Runs tool calling on DeepSeek Chat Completions (thinking disabled, as in the official example).
    """

    def __init__(self, api_key: str, model: str = DEFAULT_MODEL, base_url: str = DEFAULT_BASE_URL) -> None:
        self.client = OpenAI(api_key=api_key, base_url=base_url)
        self.model = model

    def complete(self, messages: list[dict[str, Any]], tools: list[dict[str, Any]]) -> Any:
        completion = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            tools=tools,
            temperature=0,
            extra_body={"thinking": {"type": "disabled"}},
        )
        return completion.choices[0].message


# MARK: - 工具定义与提示 / Tool definitions and prompt

SEARCH_TOOL = {
    "type": "function",
    "function": {
        "name": "web_search",
        "description": "Search the web. Supports site:, -site:, after:YYYY-MM-DD, before: and \"phrases\".",
        "parameters": {
            "type": "object",
            "properties": {"query": {"type": "string", "description": "1 to 128 characters."}},
            "required": ["query"],
        },
    },
}

FETCH_TOOL = {
    "type": "function",
    "function": {
        "name": "fetch_url",
        "description": "Open a web page and read it as Markdown. Events must come from pages opened with this tool.",
        "parameters": {
            "type": "object",
            "properties": {"url": {"type": "string"}},
            "required": ["url"],
        },
    },
}

SUBMIT_TOOL = {
    "type": "function",
    "function": {
        "name": "submit_events",
        "description": "Submit the final list of events. Call it exactly once; an empty list is fine.",
        "parameters": {
            "type": "object",
            "properties": {
                "events": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "startDate": {"type": "string", "description": "YYYY-MM-DD"},
                            "endDate": {"type": "string", "description": "YYYY-MM-DD"},
                            "startTime": {"type": ["string", "null"], "description": "24-hour HH:MM, or null"},
                            "endTime": {"type": ["string", "null"], "description": "24-hour HH:MM, or null"},
                            "timeZone": {
                                "type": ["string", "null"],
                                "enum": [*TIME_ZONES.keys(), None],
                                "description": "The time zone label as written next to the times, or null",
                            },
                            "sourceURL": {"type": "string"},
                            "quote": {"type": "string"},
                        },
                        "required": ["name", "startDate", "endDate", "sourceURL", "quote"],
                    },
                }
            },
            "required": ["events"],
        },
    },
}

ALL_TOOLS = [SEARCH_TOOL, FETCH_TOOL, SUBMIT_TOOL]


def build_system_prompt(source: AgentSource, today: date) -> str:
    """按配置生成 agent 的 system prompt。
    Builds the agent's system prompt from the config.
    """
    domains = ", ".join(source.allowed_domains)
    horizon_end = today + timedelta(days=source.horizon_days)
    zone_labels = ", ".join(TIME_ZONES.keys())
    return f"""\
You find official {source.display_name} sale events. Today is {today.isoformat()}.

Task: find {source.task}
Only include events that are running now or start before {horizon_end.isoformat()}. Skip events that already ended.

How to work:
- Use web_search to find announcements, then fetch_url to open them. You may use other sites to find leads, \
but every submitted event must come from a page on {domains} that you opened with fetch_url.
- You have at most {source.max_rounds} rounds and {source.max_pages} opened pages. Finish by calling submit_events \
exactly once; submit an empty list if you found nothing.

Each event:
- "name": the event's own name, copied exactly as it appears on the page (for example "GOG Autumn Sale"), \
not the article headline. Do not translate, shorten or rephrase it.
- One entry per event: if several pages describe the same event, submit it once.
- "startDate" / "endDate": YYYY-MM-DD as stated on the page. If the page says the event is live now and gives no \
start date, use the page's publish date (or the date shown in the search result).
- "startTime" / "endTime": 24-hour HH:MM only when the page states them, otherwise null. Do not convert time zones.
- "timeZone": the label written next to the times, one of {zone_labels}; null if no time is given.
- "sourceURL": the exact URL you opened with fetch_url.
- "quote": one sentence copied character for character from that page that contains the end date.
"""


# MARK: - agent 循环 / Agent loop


def assistant_message_dict(message: Any) -> dict[str, Any]:
    """把 SDK 返回的 assistant 消息转成可以放回 `messages` 的字典。
    Turns the SDK's assistant message into a dict that can go back into `messages`.
    """
    content = message.content
    if content is None:
        content = ""
    result: dict[str, Any] = {"role": "assistant", "content": content}
    tool_calls = message.tool_calls
    if tool_calls:
        call_dicts = []
        for call in tool_calls:
            function = {"name": call.function.name, "arguments": call.function.arguments}
            call_dicts.append({"id": call.id, "type": "function", "function": function})
        result["tool_calls"] = call_dicts
    return result


def format_search_results(results: list[dict[str, Any]]) -> str:
    """把搜索结果压成给 AI 看的短文本（标题、网址、日期、摘要）。
    Compacts search results into short text for the AI (title, URL, date, summary).
    """
    if len(results) == 0:
        return "No results."
    blocks = []
    for result in results:
        summary = result.get("summary") or result.get("snippet") or ""
        short_summary = summary[:SEARCH_SUMMARY_CHARS]
        block = f"- {result.get('title')}\n  URL: {result.get('url')}\n  Date: {result.get('date')}\n  {short_summary}"
        blocks.append(block)
    return "\n".join(blocks)


def run_agent(source: AgentSource, chat: AgentChat, tools: WebTools, now: datetime) -> AgentRun:
    """跑一次 agent 循环，直到它调用 `submit_events` 或用完轮数。
    Runs the agent loop until it calls `submit_events` or runs out of rounds.

    最后一轮只提供 `submit_events` 一个工具，让它必须交结果（DeepSeek 文档没写 `tool_choice`，不依赖它）。
    The last round offers only `submit_events` so it must submit (DeepSeek does not document `tool_choice`).

    - Parameters:
      - source: 商店配置 / The store config.
      - chat: 带工具的对话 / The tool-calling chat.
      - tools: 搜索与打开网页 / Search and page fetching.
      - now: 当前时刻（UTC）/ The current time (UTC).
    - Returns: 提交的活动与打开过的网页 / The submitted events and the opened pages.
    - Raises: `AgentError` 一直没有提交时 / `AgentError` if it never submits.
    """
    system_prompt = build_system_prompt(source, now.date())
    user_prompt = f"Find the {source.display_name} sale events now."
    messages: list[dict[str, Any]] = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]
    pages: dict[str, str] = {}
    log: list[str] = []

    for round_number in range(1, source.max_rounds + 1):
        is_last_round = round_number == source.max_rounds
        offered_tools = ALL_TOOLS
        if is_last_round:
            offered_tools = [SUBMIT_TOOL]
            messages.append({"role": "user", "content": "This is the last round. Call submit_events now."})

        message = chat.complete(messages, offered_tools)
        messages.append(assistant_message_dict(message))
        tool_calls = message.tool_calls or []
        if len(tool_calls) == 0:
            log.append(f"round {round_number}: no tool call")
            messages.append({"role": "user", "content": "Use the tools, and finish by calling submit_events."})
            continue

        for call in tool_calls:
            result_text, submission = handle_tool_call(call, source, tools, pages, log, round_number)
            messages.append({"role": "tool", "tool_call_id": call.id, "content": result_text})
            if submission is not None:
                return AgentRun(submission=submission, pages=pages, log=log)

    raise AgentError(f"no submission after {source.max_rounds} rounds", log)


def handle_tool_call(
    call: Any,
    source: AgentSource,
    tools: WebTools,
    pages: dict[str, str],
    log: list[str],
    round_number: int,
) -> tuple[str, AgentSubmission | None]:
    """执行一次工具调用，返回给 AI 的结果文本；是有效的 `submit_events` 时另外返回提交内容。
    Runs one tool call and returns the result text for the AI, plus the submission when it is a valid `submit_events`.
    """
    name = call.function.name
    try:
        arguments = json.loads(call.function.arguments)
    except json.JSONDecodeError:
        log.append(f"round {round_number}: {name} with invalid JSON arguments")
        return "Error: arguments are not valid JSON.", None

    if name == "web_search":
        query = str(arguments.get("query", ""))
        log.append(f"round {round_number}: search {query!r}")
        try:
            results = tools.search(query)
        except WebToolError as error:
            return f"Error: {error}", None
        return format_search_results(results), None

    if name == "fetch_url":
        url = str(arguments.get("url", ""))
        if url in pages:
            content = pages[url]
            return content[:PAGE_CHARS_FOR_MODEL], None
        if len(pages) >= source.max_pages:
            log.append(f"round {round_number}: page limit reached, refused {url}")
            return "Error: page limit reached. Submit what you have.", None
        log.append(f"round {round_number}: fetch {url}")
        try:
            content = tools.fetch(url)
        except WebToolError as error:
            return f"Error: {error}", None
        pages[url] = content
        return content[:PAGE_CHARS_FOR_MODEL], None

    if name == "submit_events":
        try:
            submission = AgentSubmission.model_validate(arguments)
        except ValidationError as error:
            log.append(f"round {round_number}: invalid submission")
            return f"Error: invalid events, fix and submit again: {error}", None
        log.append(f"round {round_number}: submitted {len(submission.events)} events")
        return "Submitted.", submission

    log.append(f"round {round_number}: unknown tool {name}")
    return f"Error: unknown tool {name}.", None


# MARK: - 核对与换算 / Verification and conversion


def is_allowed_url(url: str, allowed_domains: tuple[str, ...]) -> bool:
    """网址的主机是不是白名单域名（或其子域名）。
    Whether the URL's host is an allowed domain (or a subdomain of one).
    """
    host = urlsplit(url).hostname
    if host is None:
        return False
    for domain in allowed_domains:
        if host == domain or host.endswith(f".{domain}"):
            return True
    return False


def verify_source(event: AgentSubmittedEvent, run: AgentRun, source: AgentSource) -> str | None:
    """核对一条活动的来源与引文，通过返回 `None`，不通过返回原因。
    Verifies one event's source and quote; returns `None` if it passes, otherwise the reason.

    防编造的主力：来源必须是这次真正打开过的官方页，引文必须原样出现在那一页上，结束日期必须在引文里。
    The main defence against invention: the source must be an official page actually opened in this run, the quote must
    appear verbatim on it, and the end date must be in the quote.
    """
    if not is_allowed_url(event.source_url, source.allowed_domains):
        return f"source {event.source_url} is not on an allowed domain"

    page_content = run.pages.get(event.source_url)
    if page_content is None:
        return f"source {event.source_url} was not opened in this run"

    quote = collapse_spaces(event.quote)
    if quote == "" or quote not in collapse_spaces(page_content):
        return "quote is not on the source page"

    end_day = event.end_date.day
    end_day_pattern = rf"\b0?{end_day}(st|nd|rd|th)?\b"
    if re.search(end_day_pattern, quote) is None:
        return f"quote does not contain the end day {end_day}"

    return None


def to_moment(day: date, local_time: time | None, zone_label: str | None, source: AgentSource) -> datetime:
    """把日期、可选的时刻与时区标签换成 UTC；没写时刻用商店默认值，写了时刻没写时区用默认时区。
    Converts a date with an optional time and zone label to UTC; uses the store default when no time is given, and the
    default zone when only the zone is missing.
    """
    if local_time is None:
        return local_moment_to_utc(day, source.default_time, source.default_zone)

    zone = source.default_zone
    if zone_label is not None:
        zone = TIME_ZONES[zone_label]
    return local_moment_to_utc(day, local_time, zone)


def to_sale_event(event: AgentSubmittedEvent, source: AgentSource) -> SaleEvent:
    """把核对过的提交转成接口的活动。
    Builds an API event from a verified submission.
    """
    start = to_moment(event.start_date, event.start_time, event.time_zone, source)
    end = to_moment(event.end_date, event.end_time, event.time_zone, source)
    event_id = make_event_id(source.store, event.start_date, event.name)
    return SaleEvent(
        id=event_id,
        store=source.store,
        kind=source.kind,
        name=event.name,
        start=start,
        end=end,
        source_url=event.source_url,
    )
