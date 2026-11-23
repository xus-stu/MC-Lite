#!/bin/bash
# 只编译启动器类 + 插件（不构建启动器安装包、不跑 island gradle），用于插件独立发布。
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf build/classes
mkdir -p build/classes
find src -name '*.java' -print > build/sources.txt
javac --release 21 -encoding UTF-8 -d build/classes @build/sources.txt
mkdir -p build/classes/mclite
printf 'version=%s\n' "$(cat VERSION)" > build/classes/mclite/version.properties
cp -R src/mclite/assets build/classes/mclite/ 2>/dev/null || true
mkdir -p build/classes/META-INF
cp LICENSE NOTICE TRADEMARK.md build/classes/META-INF/ 2>/dev/null || true
bash plugins/build-plugins.sh
