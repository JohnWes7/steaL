"""GOG 来源：没有提前公布的日程，官方新闻只在促销开始时发，所以交给通用 agent 去搜。
GOG source: there is no published schedule and official news only appears when a sale starts, so the generic agent searches for it.

这里只有配置：要找什么、只认哪些域名、网页没写时刻时用什么。
This module is only configuration: what to look for, which domains count, and what to use when a page gives no time.
"""

from datetime import UTC, time

from .agent import AgentSource

SOURCE = AgentSource(
    store="gog",
    display_name="GOG",
    kind="sale",
    task=(
        "store-wide sales that GOG itself runs on GOG.com, such as the seasonal sales (Spring, Summer, Autumn, Winter, "
        "New Year), the anniversary sale and Black Friday. GOG announces them on its news pages (gog.com/en/news/...); "
        "searching for site:gog.com news sale with a recent after: date works well. Skip publisher, franchise or "
        "country sales, weekly deals, single-game discounts, giveaways and partner promotions (such as free Discord Nitro)."
    ),
    allowed_domains=("gog.com",),
    default_time=time(hour=8),
    default_zone=UTC,
)
"""GOG 的 agent 配置。默认时刻 8:00 UTC 是推断的：GOG 新闻写的结束时刻多是「7 / 8 AM UTC」。
GOG's agent config. The 8:00 UTC default is inferred: GOG news mostly gives end times of "7 / 8 AM UTC".
"""
