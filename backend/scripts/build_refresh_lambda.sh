#!/usr/bin/env bash
# 打包 refresh Lambda：按 uv.lock 装 Linux arm64 依赖 + sale_events 包 + 入口文件，输出到 build/refresh/。
# Builds the refresh Lambda: Linux arm64 dependencies pinned by uv.lock, the sale_events package and the entry
# files, all into build/refresh/.
#
# 用法 / Usage（在 backend/ 或任意位置 / from anywhere）: scripts/build_refresh_lambda.sh
# Terraform 之后会用 terraform_data + local-exec 调它；zip 由 Terraform 的 archive_file 打。
# Terraform will call it through terraform_data + local-exec; Terraform's archive_file makes the zip.

set -euo pipefail

BACKEND_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_ROOT="$BACKEND_ROOT/build"
PACKAGE_DIR="$BUILD_ROOT/refresh"
REQUIREMENTS="$BUILD_ROOT/refresh-requirements.txt"

# Lambda 运行时：python3.12、arm64（Graviton）。
# Lambda runtime: python3.12 on arm64 (Graviton).
PYTHON_VERSION="3.12"
PYTHON_PLATFORM="aarch64-manylinux2014"

rm -rf "$PACKAGE_DIR"
mkdir -p "$PACKAGE_DIR"
cd "$BACKEND_ROOT"

# 1. 从 uv.lock 导出精确版本（不装「最新」：云上版本要和本地测试一致）。
# 1. Export exact versions from uv.lock (never "latest": the cloud must match what was tested locally).
uv export --frozen --no-dev --no-emit-project --format requirements-txt --output-file "$REQUIREMENTS" --quiet

# 2. 按 Lambda 的平台装依赖，只要预编译的 wheel（本机是 macOS，不能编译 Linux 版）。
# 2. Install dependencies for the Lambda platform, wheels only (this machine is macOS and cannot build Linux ones).
uv pip install \
    --requirements "$REQUIREMENTS" \
    --target "$PACKAGE_DIR" \
    --python-version "$PYTHON_VERSION" \
    --python-platform "$PYTHON_PLATFORM" \
    --only-binary :all: \
    --quiet

# 3. 装 sale_events 包本身（纯 Python，不带依赖）。
# 3. Install the sale_events package itself (pure Python, without dependencies).
uv pip install . \
    --no-deps \
    --target "$PACKAGE_DIR" \
    --python-version "$PYTHON_VERSION" \
    --python-platform "$PYTHON_PLATFORM" \
    --quiet

# 4. 入口文件放在 zip 根目录，handler 写 refresh_lambda.handler。
# 4. Entry files go at the zip root; the handler is refresh_lambda.handler.
cp functions/sale_events_refresh/refresh_lambda.py functions/sale_events_refresh/s3_store.py "$PACKAGE_DIR/"

# 去掉运行时用不到的缓存。
# Drop caches the runtime does not need.
find "$PACKAGE_DIR" -type d -name "__pycache__" -prune -exec rm -rf {} +

du -sh "$PACKAGE_DIR"
