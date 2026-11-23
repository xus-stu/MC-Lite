#!/bin/bash
# 把 dist/plugins/*.jar 发布到 launcher-plugins release，并生成 plugin-manifest.json
set -euo pipefail
cd "$(dirname "$0")/.."
TAG="launcher-plugins"
REPO="xus-stu/MC-Lite"
cd dist/plugins

if ! compgen -G "*.jar" > /dev/null; then
  echo "dist/plugins 下没有插件 jar" >&2
  exit 1
fi

gh release view "$TAG" -R "$REPO" >/dev/null 2>&1 || \
  gh release create "$TAG" -R "$REPO" --title "启动器插件" \
    --notes "MC Lite 启动器可选插件。在启动器「下载中心 → 启动器插件」里安装。"

python3 - <<'PY' > /tmp/plugin-manifest.json
import glob, hashlib, json, os
entries = []
for meta in sorted(glob.glob("*.json")):
    info = json.load(open(meta, encoding="utf-8"))
    pid = info["id"]
    jars = sorted(glob.glob(pid + "-*.jar"))
    if not jars:
        continue
    path = jars[-1]
    version = os.path.basename(path)[len(pid) + 1:-4]
    data = open(path, "rb").read()
    entries.append({
        "id": pid,
        "name": info.get("name", pid),
        "version": version,
        "desc": info.get("desc", ""),
        "url": f"https://github.com/xus-stu/MC-Lite/releases/download/launcher-plugins/{os.path.basename(path)}",
        "sha256": hashlib.sha256(data).hexdigest(),
        "size": len(data),
    })
print(json.dumps({"schema": 1, "plugins": entries}, ensure_ascii=False, indent=1))
PY

for jar in *.jar; do gh release upload "$TAG" "$jar" -R "$REPO" --clobber; done
gh release upload "$TAG" /tmp/plugin-manifest.json -R "$REPO" --clobber
echo "已发布: $(ls *.jar | tr '\n' ' ')"
