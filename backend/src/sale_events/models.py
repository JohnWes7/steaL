"""促销活动的数据模型。
Data models for sale events.

接口 JSON 用 camelCase（Swift / Kotlin 默认就能解），Python 里用 snake_case，靠 alias 转换。
The API JSON uses camelCase (decodable by default on Swift / Kotlin); Python uses snake_case via aliases.
"""

from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """JSON 键用 camelCase 的基类。
    Base model whose JSON keys are camelCase.
    """

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


SteamSection = Literal["seasonalSale", "themedFest", "nextFest"]
"""Steamworks 页面上的三节，也就是 Steam 活动的 `kind`。
The three sections of the Steamworks page, which are also the `kind` of a Steam event.
"""


class ExtractedEvent(CamelModel):
    """AI 从页面上抄下来的一条活动，只有日期，没有时刻（内部用，不进接口）。
    One event as copied by the AI from the page: dates only, no times (internal, not part of the API).
    """

    section: SteamSection
    name: str
    start_date: date
    end_date: date


class ExtractionResult(CamelModel):
    """AI 一次提取的完整输出。
    The full output of one AI extraction.
    """

    events: list[ExtractedEvent]


class SaleEvent(CamelModel):
    """接口返回的一条活动，时刻都是 UTC。
    One event as returned by the API; all times are UTC.
    """

    id: str
    store: str
    kind: str
    name: str
    start: datetime
    end: datetime
    source_url: str = Field(alias="sourceURL")


class SaleEventList(CamelModel):
    """一个商店的一份数据，也是接口响应的形状。
    One store's data, in the same shape as the API response.
    """

    refreshed_at: datetime
    events: list[SaleEvent]
