"""校验 AI 提取的结果。全自动没人审核，这是唯一防线。
Validates what the AI extracted. Nothing is reviewed by a person, so this is the only safeguard.

规则见 `docs/Backend Planning.md`「已定的设计 1 · 校验」：任何一条不过，整份不覆盖、保留旧数据。
Rules follow the planning doc: if any rule fails, nothing is overwritten and the old data is kept.
结构校验（字段、类型、section、日期格式）在 `extraction.parse_extraction` 里由 pydantic 做。
The structure check (fields, types, section, date format) is done by pydantic in `extraction.parse_extraction`.
"""

from dataclasses import dataclass, field
from datetime import datetime, timedelta

from .models import SaleEvent

MAX_DURATION = timedelta(days=31)
"""最长时长。Steam 最长的冬促约 18 天、Epic 的 Holiday / MEGA Sale 28 天，超过多半是年份补错。
The longest allowed duration. Steam's longest winter sale is about 18 days and Epic's Holiday / MEGA Sale 28 days;
longer usually means a wrong year.
"""


@dataclass
class ValidationOutcome:
    """校验的结果：留下的活动、丢掉的已结束活动、错误清单。
    The validation result: kept events, dropped ended events, and the list of errors.
    """

    kept: list[SaleEvent] = field(default_factory=list)
    dropped_ended: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)

    @property
    def passed(self) -> bool:
        """没有任何错误。
        Whether there were no errors.
        """
        return len(self.errors) == 0


def validate_events(
    events: list[SaleEvent],
    page_text: str,
    previous_count: int | None,
    now: datetime,
    require_events: bool = True,
) -> ValidationOutcome:
    """按规则校验一个商店的全部活动。
    Validates all events of one store against the rules.

    「至少 1 条」只对完整日程表（Steam、Epic）有意义；itch.io 这种只在促销期间有公告的来源，空是正常的。
    "At least one event" only makes sense for complete schedules (Steam, Epic); for sources such as itch.io that
    only announce sales while they run, empty is normal.

    - Parameters:
      - events: 已经补好时刻的活动 / Events with times already applied.
      - page_text: 发给 AI 的正文，用来查名字是不是编的 / The text sent to the AI, used to catch invented names.
      - previous_count: 上一份同商店的条数，没有就是 `None` / The previous count for this store, or `None`.
      - now: 当前时刻（UTC），用来丢掉已结束的 / The current time (UTC), used to drop ended events.
      - require_events: 一条都没有算不算失败 / Whether having no events counts as a failure.
    - Returns: 校验结果 / The validation outcome.
    """
    outcome = ValidationOutcome()
    seen_ids: set[str] = set()

    for event in events:
        if event.end <= event.start:
            outcome.errors.append(f"{event.id}: end is not after start")
            continue

        duration = event.end - event.start
        if duration > MAX_DURATION:
            outcome.errors.append(f"{event.id}: lasts {duration.days} days, more than {MAX_DURATION.days}")
            continue

        # 名字必须原样出现在发给 AI 的正文里，防编造。
        # The name must appear verbatim in the text sent to the AI, to catch invented events.
        if event.name not in page_text:
            outcome.errors.append(f"{event.id}: name {event.name!r} is not on the page")
            continue

        if event.id in seen_ids:
            outcome.errors.append(f"{event.id}: duplicate id")
            continue
        seen_ids.add(event.id)

        # 已经结束的直接丢掉，不算失败。
        # Ended events are dropped and do not count as failures.
        if event.end <= now:
            outcome.dropped_ended.append(event.id)
            continue

        outcome.kept.append(event)

    kept_count = len(outcome.kept)
    if require_events and kept_count == 0:
        outcome.errors.append("no upcoming events left")

    # 条数骤降多半是页面改版或预处理切错了。
    # A sudden drop usually means the page layout changed or preprocessing cut the wrong part.
    if previous_count is not None:
        minimum_count = previous_count / 2
        if kept_count < minimum_count:
            outcome.errors.append(f"only {kept_count} events, fewer than half of the previous {previous_count}")

    return outcome
