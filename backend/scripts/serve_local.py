"""在本机起 `GET /v1/sale-events`，读 refresh 写进 `.data/` 的文件，不碰云。
Serves `GET /v1/sale-events` locally from the files refresh wrote into `.data/`, without touching the cloud.

用法 / Usage（在 `backend/` 里 / from `backend/`）:

    uv run python scripts/refresh_local.py   # 先刷新一次，写出 .data/sale-events/steam.json
    uv run python scripts/serve_local.py     # http://127.0.0.1:8000/v1/sale-events

只用标准库的 `http.server`，只在本地用，不会被打包上云。
Uses the standard library `http.server`; local only and never deployed.
"""

import argparse
import logging
import sys
from datetime import UTC, datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

BACKEND_ROOT = Path(__file__).resolve().parents[1]
API_DIR = BACKEND_ROOT / "functions" / "sale_events_api"

# api_core.py 是单独部署的单文件，不在 sale_events 包里，所以把它的目录加进 import 路径。
# api_core.py is a standalone deployed file outside the sale_events package, so its folder is added to the import path.
sys.path.insert(0, str(API_DIR))
import api_core  # noqa: E402

API_PATH = "/v1/sale-events"


def make_file_reader(data_root: Path):
    """返回一个读 `{data_root}/sale-events/*.json` 的函数（不含 `rejected/`）。
    Returns a function that reads `{data_root}/sale-events/*.json` (excluding `rejected/`).

    - Parameter data_root: 本地数据目录 / The local data directory.
    - Returns: 每次调用都重新读文件的函数 / A function that rereads the files on every call.
    """
    events_dir = data_root / "sale-events"

    def read_store_files() -> list[str]:
        if not events_dir.exists():
            return []
        paths = sorted(events_dir.glob("*.json"))
        return [path.read_text(encoding="utf-8") for path in paths]

    return read_store_files


def make_handler(read_store_files):
    """生成处理请求的类，把读文件的函数交给 `api_core.list_sale_events`。
    Builds the request handler class, passing the file reader to `api_core.list_sale_events`.
    """

    class SaleEventsHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            # 只看路径，忽略查询参数。
            # Match the path only and ignore any query string.
            request_path = urlsplit(self.path).path
            if request_path == API_PATH:
                now = datetime.now(UTC)
                response = api_core.list_sale_events(read_store_files, now)
            else:
                response = api_core.problem_response(404, "Not Found", f"Only {API_PATH} exists.")
            self.send_api_response(response)

        def send_api_response(self, response: api_core.Response) -> None:
            body = response.body.encode("utf-8")
            self.send_response(response.status)
            for name, value in response.headers.items():
                self.send_header(name, value)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    return SaleEventsHandler


def main() -> int:
    """命令行入口，返回进程退出码。
    The command-line entry point; returns the process exit code.
    """
    parser = argparse.ArgumentParser(description="Serve the sale events API locally.")
    parser.add_argument("--data", type=Path, default=BACKEND_ROOT / ".data", help="local data directory")
    parser.add_argument("--host", default="127.0.0.1", help="address to listen on")
    parser.add_argument("--port", type=int, default=8000, help="port to listen on")
    arguments = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")

    read_store_files = make_file_reader(arguments.data)
    handler_class = make_handler(read_store_files)
    server = ThreadingHTTPServer((arguments.host, arguments.port), handler_class)
    print(f"Serving http://{arguments.host}:{arguments.port}{API_PATH} from {arguments.data}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
