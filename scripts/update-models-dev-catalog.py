#!/usr/bin/env python3
"""更新 APK 内的 models.dev 离线模型目录。"""

import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import urllib.request


CATALOG_URL = "https://models.dev/api.json"
MAX_CATALOG_BYTES = 16 * 1024 * 1024
SNAPSHOT = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/assets/catalog/models-dev-api.json.gzip"
)


def download_catalog() -> bytes:
    request = urllib.request.Request(
        CATALOG_URL,
        headers={
            "User-Agent": "Eta-Catalog-Updater (+https://github.com/Mangi-11/Eta)",
            "Accept": "application/json",
            "Accept-Encoding": "identity",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.geturl() != CATALOG_URL:
            raise ValueError("模型目录跳转到了非预期地址")
        if response.headers.get_content_type() != "application/json":
            raise ValueError("模型目录不是 JSON 响应")
        declared_size = response.headers.get("Content-Length")
        if declared_size is not None and int(declared_size) > MAX_CATALOG_BYTES:
            raise ValueError("模型目录超过大小限制")
        data = response.read(MAX_CATALOG_BYTES + 1)

    if len(data) > MAX_CATALOG_BYTES:
        raise ValueError("模型目录超过大小限制")
    catalog = json.loads(data.decode("utf-8"))
    if not isinstance(catalog, dict) or not catalog:
        raise ValueError("模型目录顶层结构无效")
    if not all(
        isinstance(provider_id, str)
        and isinstance(provider, dict)
        and isinstance(provider.get("models"), dict)
        for provider_id, provider in catalog.items()
    ):
        raise ValueError("模型目录提供商结构无效")
    return data


def gzip_snapshot(data: bytes) -> bytes:
    output = io.BytesIO()
    with gzip.GzipFile(fileobj=output, mode="wb", filename="", mtime=0, compresslevel=9) as stream:
        stream.write(data)
    return output.getvalue()


def main() -> None:
    data = download_catalog()
    compressed = gzip_snapshot(data)
    SNAPSHOT.parent.mkdir(parents=True, exist_ok=True)
    if SNAPSHOT.exists() and SNAPSHOT.read_bytes() == compressed:
        print(f"目录未变化：{SNAPSHOT} ({len(compressed)} 字节)")
        return

    temporary_path = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb", dir=SNAPSHOT.parent, prefix=".models-dev-", suffix=".tmp", delete=False
        ) as temporary:
            temporary_path = Path(temporary.name)
            temporary.write(compressed)
        os.chmod(temporary_path, 0o644)
        os.replace(temporary_path, SNAPSHOT)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)

    digest = hashlib.sha256(data).hexdigest()
    print(f"已更新 {SNAPSHOT}：原始 {len(data)} 字节，gzip {len(compressed)} 字节，SHA-256 {digest}")


if __name__ == "__main__":
    main()
