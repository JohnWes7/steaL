"""与商店无关的换算：本地时刻转 UTC、生成 id。
Store-independent conversions: local time to UTC, and event ids.
"""

import re
from datetime import UTC, date, datetime, time
from zoneinfo import ZoneInfo


def slugify(name: str) -> str:
    """把名字转成小写、非字母数字换成 `-` 的 slug。
    Turns a name into a lowercase slug with non-alphanumerics replaced by `-`.
    """
    lowercased = name.lower()
    dashed = re.sub(r"[^a-z0-9]+", "-", lowercased)
    return dashed.strip("-")


def make_event_id(store: str, start_date: date, name: str) -> str:
    """生成 `{store}-{开始日期}-{名字 slug}` 形式的 id。
    Builds an id of the form `{store}-{start date}-{name slug}`.

    带开始日期是因为同名活动一年有好几场（Steam 的三场都叫「Next Fest」）。
    The start date is included because same-named events recur (Steam has three called "Next Fest").
    """
    slug = slugify(name)
    return f"{store}-{start_date.isoformat()}-{slug}"


def local_moment_to_utc(day: date, local_time: time, zone: ZoneInfo) -> datetime:
    """把某个时区里某天的某个时刻换成 UTC。
    Converts a time on a given day in a given time zone to UTC.

    用 `zoneinfo` 换算，夏令时自动处理；不让 AI 换算，因为夏令时边界容易错。
    Uses `zoneinfo` so daylight saving is handled; the AI never converts because DST edges are error-prone.
    """
    local_moment = datetime.combine(day, local_time, tzinfo=zone)
    return local_moment.astimezone(UTC)
