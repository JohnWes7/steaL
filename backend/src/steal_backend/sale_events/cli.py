"""本地跑一次 Steam 刷新，不碰云。
Runs one Steam refresh locally, without touching the cloud.

用法 / Usage（在 `backend/` 里 / from `backend/`）:

    uv run python -m steal_backend.sale_events.cli --text-only   # 只看预处理结果，不调 AI / reduced text only, no AI
    uv run python -m steal_backend.sale_events.cli               # 完整刷新，写 .data/ / full refresh into .data/

DeepSeek key 从 `backend/.env` 的 `DEEPSEEK_API_KEY` 读（模板见 `.env.example`）。
The DeepSeek key is read from `DEEPSEEK_API_KEY` in `backend/.env` (see `.env.example`).
"""

import argparse
import logging
import os
import sys
from datetime import UTC, datetime
from pathlib import Path

import httpx

from . import steam
from .extraction import DEFAULT_BASE_URL, DEFAULT_MODEL, DeepSeekExtractor
from .refresh import refresh_steam
from .storage import LocalEventStore

BACKEND_ROOT = Path(__file__).resolve().parents[3]


def main() -> int:
    """命令行入口，返回进程退出码。
    The command-line entry point; returns the process exit code.
    """
    parser = argparse.ArgumentParser(description="Refresh Steam sale events locally.")
    parser.add_argument("--text-only", action="store_true", help="print the reduced page text and stop")
    parser.add_argument("--out", type=Path, default=BACKEND_ROOT / ".data", help="local data directory")
    parser.add_argument("--verbose", action="store_true", help="debug logging")
    arguments = parser.parse_args()

    log_level = logging.INFO
    if arguments.verbose:
        log_level = logging.DEBUG
    logging.basicConfig(level=log_level, format="%(levelname)s %(name)s: %(message)s")

    http_client = httpx.Client()

    if arguments.text_only:
        page_html = steam.fetch_page(http_client)
        page_text = steam.extract_text(page_html)
        print(page_text)
        return 0

    # python-dotenv 只是本地开发依赖，function 里 key 从 Vault 读。
    # python-dotenv is a local dev dependency only; in the function the key comes from Vault.
    from dotenv import load_dotenv

    load_dotenv(BACKEND_ROOT / ".env")
    api_key = os.environ.get("DEEPSEEK_API_KEY", "")
    if api_key == "":
        print("DEEPSEEK_API_KEY is missing: copy .env.example to .env and fill it in.", file=sys.stderr)
        return 2

    model = os.environ.get("DEEPSEEK_MODEL", DEFAULT_MODEL)
    base_url = os.environ.get("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL)
    extractor = DeepSeekExtractor(api_key=api_key, model=model, base_url=base_url)
    store = LocalEventStore(arguments.out)
    now = datetime.now(UTC)

    report = refresh_steam(
        fetch_html=lambda: steam.fetch_page(http_client),
        extract=extractor.extract,
        store=store,
        now=now,
    )

    if not report.written:
        print("Rejected, nothing written. Errors:")
        for error in report.errors:
            print(f"  - {error}")
        return 1

    print(f"Wrote {len(report.events)} events to {arguments.out / 'sale-events' / 'steam.json'}")
    for event in report.events:
        print(f"  {event.start:%Y-%m-%d %H:%MZ} → {event.end:%Y-%m-%d %H:%MZ}  {event.kind:<12}  {event.name}")
    if report.dropped_ended:
        print(f"Dropped ended: {', '.join(report.dropped_ended)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
