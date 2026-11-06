"""促销活动的数据模型。
Data models for sale events.

接口 JSON 用 camelCase（Swift / Kotlin 默认就能解），Python 里用 snake_case，靠 alias 转换。
The API JSON uses camelCase (decodable by default on Swift / Kotlin); Python uses snake_case via aliases.
"""

from datetime import date, datetime
from typing import Annotated

from pydantic import AfterValidator, BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


def drop_microseconds(value: datetime) -> datetime:
    """去掉小数秒，只保留到整秒。
    Drops fractional seconds, keeping whole seconds only.

    接口约定时刻都是整秒（`2026-12-17T18:00:00Z`）：iOS `JSONDecoder` 的 `.iso8601` 不认小数秒。
    The API promises whole-second times (`2026-12-17T18:00:00Z`): iOS `JSONDecoder`'s `.iso8601` rejects fractional seconds.
    """
    return value.replace(microsecond=0)


WholeSecondTime = Annotated[datetime, AfterValidator(drop_microseconds)]
"""接口里的时刻：建模型时自动截到整秒。
A time in the API: truncated to whole seconds when the model is built.
"""


class CamelModel(BaseModel):
    """JSON 键用 camelCase 的基类。
    Base model whose JSON keys are camelCase.
    """

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ExtractedEvent(CamelModel):
    """AI 从页面上抄下来的一条活动，只有日期，没有时刻（内部用，不进接口）。
    One event as copied by the AI from the page: dates only, no times (internal, not part of the API).

    各商店需要额外字段时（如 Steam 的 `section`）在自己的模块里继承它。
    Stores that need extra fields (such as Steam's `section`) subclass it in their own module.
    """

    name: str
    start_date: date
    end_date: date


class SaleEvent(CamelModel):
    """接口返回的一条活动，时刻都是 UTC。
    One event as returned by the API; all times are UTC.
    """

    id: str
    store: str
    kind: str
    name: str
    start: WholeSecondTime
    end: WholeSecondTime
    source_url: str = Field(alias="sourceURL")


class SaleEventList(CamelModel):
    """一个商店的一份数据，也是接口响应的形状。
    One store's data, in the same shape as the API response.
    """

    refreshed_at: WholeSecondTime
    events: list[SaleEvent]
