#!/usr/bin/env bash
# FlowMini 构建脚本：编译 → 打包 .app → 生成图标 → 签名 → zip / dmg
set -euo pipefail
cd "$(dirname "$0")"

ROOT="$PWD"
SDK="$(xcrun --sdk macosx --show-sdk-path)"
BUILD="$ROOT/.build"
DIST="$ROOT/dist"
APP="$DIST/FlowMini.app"
VERSION="1.0"
ARCHS="${ARCHS:-arm64}"

export TMPDIR="$BUILD/tmp"
mkdir -p "$BUILD/tmp" "$BUILD/modcache" "$DIST"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"

echo "==> 编译 (${ARCHS})"
BINARIES=()
for ARCH in $ARCHS; do
  mkdir -p "$BUILD/modcache-$ARCH"
  swiftc \
    -O -whole-module-optimization -swift-version 5 \
    -target "$ARCH-apple-macosx14.0" \
    -sdk "$SDK" \
    -module-cache-path "$BUILD/modcache-$ARCH" \
    "$ROOT"/Sources/*.swift \
    -o "$BUILD/FlowMini-$ARCH"
  BINARIES+=("$BUILD/FlowMini-$ARCH")
done

if [ "${#BINARIES[@]}" -gt 1 ]; then
  lipo -create "${BINARIES[@]}" -output "$APP/Contents/MacOS/FlowMini"
else
  cp "${BINARIES[0]}" "$APP/Contents/MacOS/FlowMini"
fi
chmod +x "$APP/Contents/MacOS/FlowMini"

cp "$ROOT/Resources/Info.plist" "$APP/Contents/Info.plist"
printf 'APPL????' > "$APP/Contents/PkgInfo"

echo "==> 图标"
rm -rf "$BUILD/AppIcon.iconset"
"$APP/Contents/MacOS/FlowMini" --icon "$BUILD" > /dev/null
iconutil -c icns "$BUILD/AppIcon.iconset" -o "$APP/Contents/Resources/AppIcon.icns"

echo "==> 签名"
codesign --force --sign - --timestamp=none "$APP" >/dev/null 2>&1 || true
codesign --force --sign - --timestamp=none "$APP/Contents/MacOS/FlowMini" >/dev/null 2>&1 || true
codesign --force --sign - --timestamp=none "$APP" >/dev/null 2>&1 || true

echo "==> 归档"
rm -rf "$BUILD/stage"
mkdir -p "$BUILD/stage"
cp -R "$APP" "$BUILD/stage/"
mkdir -p "$BUILD/stage/示例"
cp "$ROOT"/samples/*.flow "$BUILD/stage/示例/" 2>/dev/null || true
ln -s /Applications "$BUILD/stage/Applications"

rm -f "$DIST/FlowMini-$VERSION.zip" "$DIST/FlowMini-$VERSION.dmg"
ditto -c -k --keepParent "$APP" "$DIST/FlowMini-$VERSION.zip"

# DMG 需要本机允许挂载磁盘映像；失败时只保留 zip
if hdiutil create -quiet -volname "FlowMini" -srcfolder "$BUILD/stage" -ov -format UDZO "$DIST/FlowMini-$VERSION.dmg" >/dev/null 2>&1; then
  DMG="$DIST/FlowMini-$VERSION.dmg"
else
  DMG=""
  echo "（本机无法创建 dmg，已跳过，使用 zip 即可）"
fi

echo
echo "完成："
du -sh "$APP" "$DIST/FlowMini-$VERSION.zip"
[ -n "$DMG" ] && du -sh "$DMG"
echo "打开：open \"$APP\""
