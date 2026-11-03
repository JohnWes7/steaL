"""本地跑一次刷新（Steam、Epic、itch.io、GOG，或全部），不碰云。
Runs one refresh locally (Steam, Epic, itch.io, GOG, or all of them), without touching the cloud.

用法 / Usage（在 `backend/` 里 / from `backend/`）:

    uv run python scripts/refresh_local.py --text-only           # 只看预处理结果，不调 AI / reduced text only, no AI
    uv run python scripts/refresh_local.py                       # 全部商店都刷新，写 .data/ / refresh all into .data/
    uv run python scripts/refresh_local.py --store epic          # 只刷新一个商店 / refresh one store only

DeepSeek key 从 `backend/.env` 的 `DEEPSEEK_API_KEY` 读（模板见 `.env.example`）。
The DeepSeek key is read from `DEEPSEEK_API_KEY` in `backend/.env` (see `.env.example`).

只在本地用，不进 `sale_events` 包，也不会被打包上云。
Local only: it is not part of the `sale_events` package and is never deployed.
"""

import argparse
import logging
import os
import sys
from datetime import UTC, datetime
from pathlib import Path

import httpx

from sale_events import epic, itchio, steam
from sale_events.extraction import DEFAULT_BASE_URL, DEFAULT_MODEL
from sale_events.refresh import RefreshReport
from sale_events.runner import STORES, DeepSeekSettings, run_store
from sale_events.storage import LocalEventStore

BACKEND_ROOT = Path(__file__).resolve().parents[1]
STORE_CHOICES = [*STORES, "all"]


def main() -> int:
    """命令行入口，返回进程退出码。
    The command-line entry point; returns the process exit code.
    """
    parser = argparse.ArgumentParser(description="Refresh sale events locally.")
    parser.add_argument("--store", choices=STORE_CHOICES, default="all", help="which store to refresh")
    parser.add_argument("--text-only", action="store_true", help="print the reduced page text and stop")
    parser.add_argument("--out", type=Path, default=BACKEND_ROOT / ".data", help="local data directory")
    parser.add_argument("--verbose", action="store_true", help="debug logging")
    arguments = parser.parse_args()

    log_level = logging.INFO
    if arguments.verbose:
        log_level = logging.DEBUG
    logging.basicConfig(level=log_level, format="%(levelname)s %(name)s: %(message)s")

    http_client = httpx.Client()
    run_steam = arguments.store in ("steam", "all")
    run_epic = arguments.store in ("epic", "all")
    run_itchio = arguments.store in ("itchio", "all")
    run_gog = arguments.store in ("gog", "all")
    now = datetime.now(UTC)

    if arguments.text_only:
        if run_steam:
            page_html = steam.fetch_page(http_client)
            page_text = steam.extract_text(page_html)
            print(page_text)
        if run_epic:
            guides = epic.fetch_guides(http_client)
            for guide in guides:
                print(f"\n----- {guide.url}\n")
                print(epic.extract_text(guide))
        if run_itchio:
            posts = itchio.fetch_posts(http_client, now)
            for post in posts:
                print(f"\n----- {post.url}\n")
                print(itchio.extract_text(post))
        if run_gog:
            print("\nGOG uses the agent, which has no reduced text to print.")
        return 0

    # python-dotenv 只是本地开发依赖，上云后 key 从云的 secret 服务读。
    # python-dotenv is a local dev dependency only; in the cloud the key comes from the provider's secret service.
    from dotenv import load_dotenv

    load_dotenv(BACKEND_ROOT / ".env")
    api_key = os.environ.get("DEEPSEEK_API_KEY", "")
    if api_key == "":
        print("DEEPSEEK_API_KEY is missing: copy .env.example to .env and fill it in.", file=sys.stderr)
        return 2

    model = os.environ.get("DEEPSEEK_MODEL", DEFAULT_MODEL)
    base_url = os.environ.get("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL)
    settings = DeepSeekSettings(api_key=api_key, model=model, base_url=base_url)
    store = LocalEventStore(arguments.out)

    exit_code = 0
    for store_name in STORES:
        if arguments.store not in (store_name, "all"):
            continue
        report = run_store(store_name, settings, http_client, store, now)
        succeeded = print_report(store_name, report, arguments.out)
        if not succeeded:
            exit_code = 1

    return exit_code


def print_report(store_name: str, report: RefreshReport, out: Path) -> bool:
    """打印一个商店的刷新结果，返回是否写入成功。
    Prints one store's refresh result and returns whether it was written.
    """
    if not report.written:
        print(f"[{store_name}] Rejected, nothing written. Errors:")
        for error in report.errors:
            print(f"  - {error}")
        return False

    print(f"[{store_name}] Wrote {len(report.events)} events to {out / 'sale-events' / f'{store_name}.json'}")
    for event in report.events:
        print(f"  {event.start:%Y-%m-%d %H:%MZ} → {event.end:%Y-%m-%d %H:%MZ}  {event.kind:<12}  {event.name}")
    if report.dropped_ended:
        print(f"  Dropped ended: {', '.join(report.dropped_ended)}")
    if report.dropped_unverified:
        print("  Dropped unverified:")
        for reason in report.dropped_unverified:
            print(f"    - {reason}")
    return True


if __name__ == "__main__":
    sys.exit(main())
