"""与商店无关的换算：本地时刻转 UTC、生成 id、整理空白。
Store-independent conversions: local time to UTC, event ids, and whitespace cleanup.
"""

import re
from datetime import UTC, date, datetime, time, tzinfo


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


def local_moment_to_utc(day: date, local_time: time, zone: tzinfo) -> datetime:
    """把某个时区里某天的某个时刻换成 UTC。
    Converts a time on a given day in a given time zone to UTC.

    用 `zoneinfo` 换算，夏令时自动处理；不让 AI 换算，因为夏令时边界容易错。
    Uses `zoneinfo` so daylight saving is handled; the AI never converts because DST edges are error-prone.
    """
    local_moment = datetime.combine(day, local_time, tzinfo=zone)
    return local_moment.astimezone(UTC)


def collapse_spaces(text: str) -> str:
    """把连续空白（含换行）压成一个空格，去掉首尾空白。
    Collapses runs of whitespace (including newlines) into single spaces and trims the ends.

    不用 `get_text(strip=True)`：它会把每段文字的首尾空格去掉再拼起来，`going <i>fast</i>` 会变成 `goingfast`。
    `get_text(strip=True)` is avoided because it trims each fragment before joining, turning `going <i>fast</i>` into `goingfast`.
    """
    words = text.split()
    return " ".join(words)
