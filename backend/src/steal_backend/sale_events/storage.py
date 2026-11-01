"""活动数据的存储：每个商店一个 JSON 文件，校验不过的存进 rejected。
Storage for event data: one JSON file per store, plus rejected outputs.

本地开发用 `LocalEventStore` 写文件；上云后换成 Object Storage 的实现，接口相同。
Local development writes files with `LocalEventStore`; the cloud version will use Object Storage with the same interface.
"""

import json
from datetime import datetime
from pathlib import Path
from typing import Any, Protocol

from .models import SaleEventList


class EventStore(Protocol):
    """活动数据存储的接口。
    The interface for event data storage.
    """

    def read(self, store: str) -> SaleEventList | None:
        """读某个商店的当前数据，还没有就返回 `None`。
        Reads a store's current data, or `None` if there is none yet.
        """
        ...

    def write(self, store: str, event_list: SaleEventList) -> None:
        """整份写入某个商店的数据。
        Writes a store's data as a whole.
        """
        ...

    def write_rejected(self, store: str, rejected_at: datetime, payload: dict[str, Any]) -> None:
        """保存一次校验不过的 AI 输出与错误清单。
        Saves an AI output that failed validation, with its errors.
        """
        ...


class LocalEventStore:
    """写本地文件的存储，路径布局和 Object Storage 一样（`sale-events/{store}.json`）。
    File-based storage with the same layout as Object Storage (`sale-events/{store}.json`).
    """

    def __init__(self, root: Path) -> None:
        self.root = root

    def read(self, store: str) -> SaleEventList | None:
        path = self._store_path(store)
        if not path.exists():
            return None
        text = path.read_text(encoding="utf-8")
        return SaleEventList.model_validate_json(text)

    def write(self, store: str, event_list: SaleEventList) -> None:
        path = self._store_path(store)
        path.parent.mkdir(parents=True, exist_ok=True)
        text = event_list.model_dump_json(by_alias=True, indent=2)
        # 先写临时文件再改名，读的一方不会读到写了一半的数据（同 Object Storage 覆盖写是原子的）。
        # Write to a temp file then rename, so readers never see a half-written file (like Object Storage).
        temp_path = path.with_suffix(".json.tmp")
        temp_path.write_text(text + "\n", encoding="utf-8")
        temp_path.replace(path)

    def write_rejected(self, store: str, rejected_at: datetime, payload: dict[str, Any]) -> None:
        timestamp = rejected_at.strftime("%Y%m%dT%H%M%SZ")
        path = self.root / "sale-events" / "rejected" / f"{store}-{timestamp}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        text = json.dumps(payload, ensure_ascii=False, indent=2, default=str)
        path.write_text(text + "\n", encoding="utf-8")

    def _store_path(self, store: str) -> Path:
        return self.root / "sale-events" / f"{store}.json"
