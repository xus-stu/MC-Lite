#!/usr/bin/env python3
"""生成 MCLite 岛的可选组件清单（island-manifest.json）。

用法：
  python3 island/tools/gen_manifest.py --out dist/island-manifest.json \
      --tag v1.4.0 island/versions/*/*/build/libs/*-bundled.jar

文件名约定：mclite-island-<mc>-<loader>-<version>-bundled.jar。
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

NAME_RE = re.compile(
    r"^mclite-island-(?P<mc>[0-9][0-9A-Za-z._-]*?)-(?P<loader>fabric|forge|neoforge)-"
    r"(?P<version>[0-9][0-9A-Za-z._+-]*?)(?:-bundled)?\.jar$"
)


def java_for(mc: str) -> int:
    if mc.startswith("1.20"):
        return 17
    if mc.startswith("1.21"):
        return 21
    if mc.startswith("26."):
        return 25
    return 21


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("--tag", required=True, help="release tag，例如 v1.4.0")
    parser.add_argument("--repo", default="xus-stu/MC-Lite")
    parser.add_argument("jars", nargs="+")
    args = parser.parse_args()

    builds = []
    for raw in args.jars:
        path = Path(raw)
        match = NAME_RE.match(path.name)
        if not match:
            print(f"跳过不认识的文件名: {path}", file=sys.stderr)
            continue
        mc = match.group("mc")
        loader = match.group("loader")
        version = match.group("version")
        published = f"mclite-island-{mc}-{loader}-{version}.jar"
        builds.append(
            {
                "version": version,
                "mc": mc,
                "loader": loader,
                "java": java_for(mc),
                "url": f"https://github.com/{args.repo}/releases/download/{args.tag}/{published}",
                "sha256": sha256(path),
                "size": path.stat().st_size,
            }
        )

    builds.sort(key=lambda item: (item["mc"], item["loader"], item["version"]))
    manifest = {"schema": 1, "builds": builds}
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"写入 {out}（{len(builds)} 个构建）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
