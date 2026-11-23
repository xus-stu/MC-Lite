#!/bin/bash
# 构建 plugins/* 下的所有启动器插件（需要 build/classes 里的启动器类）。
set -euo pipefail
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
  echo "build/classes 不存在：先编译启动器源码" >&2
  exit 1
fi
rm -rf dist/plugins
mkdir -p dist/plugins
for dir in plugins/*/; do
  [ -f "$dir/resources/plugin.json" ] || continue
  [ -d "$dir/src" ] || continue
  id=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1],encoding='utf-8'))['id'])" "$dir/resources/plugin.json")
  version=$(cat "$dir/version.txt")
  rm -rf build/plugin-classes
  mkdir -p build/plugin-classes
  javac --release 21 -encoding UTF-8 -cp build/classes -d build/plugin-classes \
    $(find "$dir/src" -name '*.java')
  cp -R "$dir/resources/." build/plugin-classes/
  jar --create --file "dist/plugins/${id}-${version}.jar" -C build/plugin-classes .
  cp "$dir/resources/plugin.json" "dist/plugins/${id}.json"
  echo "  ${id}-${version}.jar"
done
