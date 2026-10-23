#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

echo "[1/4] 编译 Java 源码…"
rm -rf build/classes
mkdir -p build/classes dist
find src -name '*.java' -print > build/sources.txt
javac --release 21 -encoding UTF-8 -d build/classes @build/sources.txt
if [ -d src/mclite/assets ]; then
  mkdir -p build/classes/mclite/assets
  cp src/mclite/assets/*.png build/classes/mclite/assets/
fi
mkdir -p build/classes/META-INF build/region-marker/mclite
cp LICENSE NOTICE TRADEMARK.md build/classes/META-INF/
: > build/region-marker/mclite/region.lock

echo "[2/4] 打包 MCLite.jar…"
jar --create --file dist/MCLite.jar --main-class Main -C build/classes .
jar --create --file dist/MCLite-region.jar --main-class mclite.RegionLauncherApp -C build/classes . -C build/region-marker .
rm -f dist/MC-Lite-macOS.zip

if [[ "$(uname -s)" == "Darwin" ]]; then
  echo "[3/4] 生成 macOS 应用包…"
  APP="dist/MC Lite.app"
  rm -rf "$APP"
  mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
  cp dist/MCLite.jar "$APP/Contents/Resources/MCLite.jar"
  cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDisplayName</key>
    <string>MC Lite</string>
    <key>CFBundleExecutable</key>
    <string>MCLite</string>
    <key>CFBundleIdentifier</key>
    <string>com.mclite.launcher</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>MC Lite</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.1</string>
    <key>CFBundleVersion</key>
    <string>1.0.1</string>
    <key>LSApplicationCategoryType</key>
    <string>public.app-category.games</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.15</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
PLIST
  printf 'APPL????' > "$APP/Contents/PkgInfo"
  cat > "$APP/Contents/MacOS/MCLite" <<'LAUNCHER'
#!/bin/bash
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
JAR="$HERE/../Resources/MCLite.jar"
JAVA=""
if [ -x /usr/libexec/java_home ]; then
  for candidate in 21 22 23 24 25; do
    HOME_DIR="$(/usr/libexec/java_home -v "$candidate" 2>/dev/null || true)"
    if [ -n "$HOME_DIR" ] && [ -x "$HOME_DIR/bin/java" ]; then
      JAVA="$HOME_DIR/bin/java"
      break
    fi
  done
  if [ -z "$JAVA" ]; then
    HOME_DIR="$(/usr/libexec/java_home 2>/dev/null || true)"
    if [ -n "$HOME_DIR" ] && [ -x "$HOME_DIR/bin/java" ]; then
      JAVA="$HOME_DIR/bin/java"
    fi
  fi
fi
if [ -z "$JAVA" ] && [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
fi
for candidate in /opt/homebrew/opt/openjdk/bin/java /usr/local/opt/openjdk/bin/java; do
  if [ -z "$JAVA" ] && [ -x "$candidate" ]; then
    JAVA="$candidate"
  fi
done
if [ -z "$JAVA" ] && command -v java >/dev/null 2>&1; then
  JAVA="$(command -v java)"
fi
if [ -z "$JAVA" ] && [ -x /usr/bin/java ]; then
  JAVA=/usr/bin/java
fi
if [ -z "$JAVA" ]; then
  osascript -e 'display dialog "没有找到 Java 21 或更高版本。\n请安装 JDK 21+ 后再打开 MC Lite。" buttons {"好"} default button 1 with icon caution' >/dev/null 2>&1 || true
  exit 1
fi
VERSION_OUTPUT="$("$JAVA" -version 2>&1)"
VERSION_LINE="$(echo "$VERSION_OUTPUT" | grep -E ' version ' | head -n 1)"
if ! echo "$VERSION_OUTPUT" | grep -Eq ' version "(2[1-9]|[3-9][0-9])'; then
  osascript -e "display dialog \"当前 Java 版本过低：\n$VERSION_LINE\n\nMC Lite 需要 Java 21 或更高版本。\" buttons {\"好\"} default button 1 with icon caution" >/dev/null 2>&1 || true
  exit 1
fi
exec "$JAVA" -Xms32m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "$JAR" "$@"
LAUNCHER
  chmod +x "$APP/Contents/MacOS/MCLite"
  if command -v ditto >/dev/null 2>&1; then
    ditto -c -k --norsrc --keepParent "$APP" dist/MC-Lite-macOS.zip
  fi
  REGION_APP="dist/MC Lite Region.app"
  rm -rf "$REGION_APP"
  cp -R "$APP" "$REGION_APP"
  rm -f "$REGION_APP/Contents/Resources/MCLite.jar"
  cp dist/MCLite-region.jar "$REGION_APP/Contents/Resources/MCLite-region.jar"
  if ! unzip -p "$REGION_APP/Contents/Resources/MCLite-region.jar" META-INF/MANIFEST.MF | grep -q RegionLauncherApp; then
    echo "错误：地区版 app 内的 jar 不是 RegionLauncherApp" >&2
    exit 1
  fi
  if ! unzip -l "$REGION_APP/Contents/Resources/MCLite-region.jar" | grep -q 'mclite/region.lock'; then
    echo "错误：地区版 app 内的 jar 缺少 region.lock" >&2
    exit 1
  fi
  perl -0pi -e 's/MCLite\.jar/MCLite-region.jar/g' "$REGION_APP/Contents/MacOS/MCLite"
  perl -0pi -e 's/<string>MC Lite<\/string>/<string>MC Lite Region<\/string>/g; s/com\.mclite\.launcher/com.mclite.launcher.region/g' "$REGION_APP/Contents/Info.plist"
  if command -v ditto >/dev/null 2>&1; then
    ditto -c -k --norsrc --keepParent "$REGION_APP" dist/MC-Lite-region-macOS.zip
  fi
else
  echo "[3/4] 非 macOS，跳过应用包。"
fi

cat > run.sh <<'RUN'
#!/bin/bash
set -e
cd "$(dirname "$0")"
if [ ! -f dist/MCLite.jar ]; then
  ./build.sh
fi
exec java -Xms32m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar dist/MCLite.jar "$@"
RUN
chmod +x run.sh

cat > 启动器.command <<'CMD'
#!/bin/bash
cd "$(dirname "$0")"
exec ./run.sh "$@"
CMD
chmod +x 启动器.command

cat > run-region.sh <<'RUN'
#!/bin/bash
set -e
cd "$(dirname "$0")"
if [ ! -f dist/MCLite-region.jar ]; then
  ./build.sh
fi
exec java -Xms32m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar dist/MCLite-region.jar "$@"
RUN
chmod +x run-region.sh

cat > 启动器-地区版.command <<'REGIONCMD'
#!/bin/bash
cd "$(dirname "$0")"
exec ./run-region.sh "$@"
REGIONCMD
chmod +x 启动器-地区版.command

cat > run-region.bat <<'BAT'
@echo off
setlocal
cd /d "%~dp0"
if not exist "dist\MCLite-region.jar" (
  echo MCLite-region.jar not found.
  pause
  exit /b 1
)
java -Xms32m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "dist\MCLite-region.jar" %*
BAT

rm -rf build/windows
mkdir -p build/windows/MC-Lite-Region
cp dist/MCLite-region.jar build/windows/MC-Lite-Region/
cp LICENSE NOTICE TRADEMARK.md build/windows/MC-Lite-Region/
cp packaging/windows/start.bat packaging/windows/debug.bat packaging/windows/README.txt packaging/windows/java-download.txt build/windows/MC-Lite-Region/
(cd build/windows && zip -rq ../../dist/MC-Lite-region-Windows.zip MC-Lite-Region)
rm -f dist/MC-Lite-region-Windows.zip.sha256

if command -v shasum >/dev/null 2>&1; then
  (cd dist && files="MCLite-region.jar"; for z in MC-Lite-region-macOS.zip MC-Lite-region-Windows.zip; do [ -f "$z" ] && files="$files $z"; done; shasum -a 256 $files > SHA256SUMS.txt)
else
  (cd dist && files="MCLite-region.jar"; for z in MC-Lite-region-macOS.zip MC-Lite-region-Windows.zip; do [ -f "$z" ] && files="$files $z"; done; sha256sum $files > SHA256SUMS.txt)
fi

echo "[4/4] 完成。"
echo "  图形界面: dist/MC Lite.app（macOS）"
echo "  macOS 压缩包: dist/MC-Lite-macOS.zip"
echo "  通用 JAR : dist/MCLite.jar"
echo "  地区版   : dist/MCLite-region.jar"
echo "  校验值   : dist/SHA256SUMS.txt"
