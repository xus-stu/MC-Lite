#!/bin/bash
set -e
cd "$(dirname "$0")"
VERSION="$(cat VERSION)"
DEST="神秘小文件/v${VERSION}"
mkdir -p "$DEST"
[ -f "dist/MCLite_v${VERSION}.jar" ] && cp "dist/MCLite_v${VERSION}.jar" "$DEST/"
[ -f "dist/MCLite_v${VERSION}.exe" ] && cp "dist/MCLite_v${VERSION}.exe" "$DEST/"
[ -f "dist/MC-Lite_v${VERSION}_macOS.zip" ] && cp "dist/MC-Lite_v${VERSION}_macOS.zip" "$DEST/"
[ -d "dist/MC Lite v${VERSION}.app" ] && cp -R "dist/MC Lite v${VERSION}.app" "$DEST/"
echo "备份完成: $DEST"
