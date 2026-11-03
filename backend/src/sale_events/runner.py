"""按商店名接好抓取、AI 与存储，跑一次刷新。本地脚本和云上入口共用，与云无关。
Wires fetching, the AI and storage for a store by name and runs one refresh. Shared by the local script and cloud
entry points, and independent of any cloud.
"""

from dataclasses import dataclass
from datetime import datetime

import httpx

from . import agent, epic, gog, itchio, steam
from .extraction import DeepSeekExtractor
from .refresh import RefreshReport, refresh_epic, refresh_itchio, refresh_steam, refresh_with_agent
from .storage import EventStore

STORES = (steam.STORE, epic.STORE, itchio.STORE, gog.SOURCE.store)
"""能刷新的商店名，也是存储里的文件名。
The store names that can be refreshed, which are also the file names in storage.
"""


@dataclass
class DeepSeekSettings:
    """调用 DeepSeek 需要的 key、模型名与地址。
    The key, model name and base URL needed to call DeepSeek.
    """

    api_key: str
    model: str
    base_url: str


class UnknownStoreError(Exception):
    """商店名不在 `STORES` 里。
    The store name is not in `STORES`.
    """


def run_store(
    store_name: str,
    settings: DeepSeekSettings,
    http_client: httpx.Client,
    event_store: EventStore,
    now: datetime,
) -> RefreshReport:
    """刷新一个商店。
    Refreshes one store.

    - Parameters:
      - store_name: 商店名，`STORES` 之一 / The store name, one of `STORES`.
      - settings: DeepSeek 设置 / The DeepSeek settings.
      - http_client: 抓网页用的 httpx 客户端 / The httpx client used to fetch pages.
      - event_store: 读写数据的存储 / The storage to read and write.
      - now: 当前时刻（UTC）/ The current time (UTC).
    - Returns: 这次刷新的结果 / The result of this refresh.
    - Raises: `UnknownStoreError` 商店名不认识时 / `UnknownStoreError` for an unknown store name.
    """
    extractor = DeepSeekExtractor(api_key=settings.api_key, model=settings.model, base_url=settings.base_url)

    if store_name == steam.STORE:
        return refresh_steam(
            fetch_html=lambda: steam.fetch_page(http_client),
            extract=extractor.extract,
            store=event_store,
            now=now,
        )

    if store_name == epic.STORE:
        return refresh_epic(
            fetch_guides=lambda: epic.fetch_guides(http_client),
            extract=extractor.extract,
            store=event_store,
            now=now,
        )

    if store_name == itchio.STORE:
        return refresh_itchio(
            fetch_posts=lambda: itchio.fetch_posts(http_client, now),
            extract=extractor.extract,
            store=event_store,
            now=now,
        )

    if store_name == gog.SOURCE.store:
        chat = agent.DeepSeekChat(api_key=settings.api_key, model=settings.model, base_url=settings.base_url)
        web_tools = agent.DeepSeekWebTools(api_key=settings.api_key, client=http_client, base_url=settings.base_url)
        return refresh_with_agent(
            source=gog.SOURCE,
            run_agent=lambda: agent.run_agent(gog.SOURCE, chat, web_tools, now),
            store=event_store,
            now=now,
        )

    raise UnknownStoreError(f"unknown store {store_name!r}")
