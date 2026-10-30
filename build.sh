#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
VERSION="$(cat VERSION)"
NORMAL_JAR="MCLite_v${VERSION}.jar"
REGION_JAR="MCLite_v${VERSION}-region.jar"
MAC_ZIP="MC-Lite_v${VERSION}_macOS.zip"
REGION_MAC_ZIP="MC-Lite_v${VERSION}_region_macOS.zip"
WIN_ZIP="MC-Lite_v${VERSION}_region_Windows.zip"
APP="dist/MC Lite v${VERSION}.app"
REGION_APP="dist/MC Lite Region v${VERSION}.app"

# 删除 dist 里不属于当前版本的旧构建产物，构建目录只保留当前版本。
for f in dist/MCLite_v*.jar dist/MCLite_v*.exe dist/MC-Lite_v*_Setup.exe \
         dist/MC-Lite_v*_macOS.zip dist/MC-Lite_v*_region_macOS.zip \
         dist/MC-Lite_v*_region_Windows.zip; do
  [ -e "$f" ] || continue
  case "$f" in
    *"${VERSION}"*) ;;
    *) rm -f "$f" ;;
  esac
done
for d in dist/"MC Lite v"*.app dist/"MC Lite Region v"*.app; do
  [ -d "$d" ] || continue
  case "$d" in
    *"${VERSION}"*) ;;
    *) rm -rf "$d" ;;
  esac
done

echo "[1/4] 编译 Java 源码…"
rm -rf build/classes
mkdir -p build/classes dist
find src -name '*.java' -print > build/sources.txt
javac --release 21 -encoding UTF-8 -d build/classes @build/sources.txt
if [ -d src/mclite/assets ]; then
  mkdir -p build/classes/mclite/assets
  cp src/mclite/assets/*.png build/classes/mclite/assets/
fi
mkdir -p build/classes/META-INF build/region-marker/mclite build/classes/mclite
cp LICENSE NOTICE TRADEMARK.md build/classes/META-INF/
if [ -d src/META-INF/services ]; then
  mkdir -p build/classes/META-INF/services
  cp src/META-INF/services/* build/classes/META-INF/services/
fi
printf 'version=%s\n' "$VERSION" > build/classes/mclite/version.properties
: > build/region-marker/mclite/region.lock

echo "[2/4] 打包 ${NORMAL_JAR}…"
rm -f "dist/${NORMAL_JAR}" "dist/${REGION_JAR}" "dist/${MAC_ZIP}" "dist/${REGION_MAC_ZIP}" "dist/${WIN_ZIP}"
jar --create --file "dist/${NORMAL_JAR}" --main-class Main -C build/classes .
jar --create --file "dist/${REGION_JAR}" --main-class mclite.RegionLauncherApp -C build/classes . -C build/region-marker .

echo "[2/4] 打包可选跑分模块…"
rm -rf build/benchmark-classes
mkdir -p build/benchmark-classes
javac --release 21 -encoding UTF-8 -d build/benchmark-classes benchmark/src/mclite/benchmark/BenchmarkMain.java
printf 'Main-Class: mclite.benchmark.BenchmarkMain\n' > build/benchmark-manifest.txt
jar --create --file dist/MCLiteBenchmark.jar --manifest build/benchmark-manifest.txt -C build/benchmark-classes .

if [[ "$(uname -s)" == "Darwin" ]]; then
  echo "[3/4] 生成 macOS 应用包…"
  rm -rf "$APP"
  mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
  cp assets/AppIcon.icns "$APP/Contents/Resources/AppIcon.icns"
  cp "dist/${NORMAL_JAR}" "$APP/Contents/Resources/${NORMAL_JAR}"
  cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDisplayName</key>
    <string>MC Lite v${VERSION}</string>
    <key>CFBundleExecutable</key>
    <string>MCLite</string>
    <key>CFBundleIdentifier</key>
    <string>com.mclite.launcher</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>MC Lite v${VERSION}</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>${VERSION}</string>
    <key>CFBundleVersion</key>
    <string>${VERSION}</string>
    <key>LSApplicationCategoryType</key>
    <string>public.app-category.games</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.15</string>
    <key>NSHighResolutionCapable</key>
    <true/>
  <key>CFBundleIconFile</key>
  <string>AppIcon.icns</string>
</dict>
</plist>
PLIST
  printf 'APPL????' > "$APP/Contents/PkgInfo"
  cat > "$APP/Contents/MacOS/MCLite" <<'LAUNCHER'
#!/bin/bash
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
JAR="$HERE/../Resources/@JAR@"
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
exec "$JAVA" -Xms32m -Xmx256m -Dsun.java2d.opengl=false -Dsun.java2d.metal=false -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "$JAR" "$@"
LAUNCHER
  perl -0pi -e "s/\@JAR\@/${NORMAL_JAR}/g" "$APP/Contents/MacOS/MCLite"
  chmod +x "$APP/Contents/MacOS/MCLite"
  if command -v ditto >/dev/null 2>&1; then
    ditto -c -k --norsrc --keepParent "$APP" "dist/${MAC_ZIP}"
  fi
  rm -rf "$REGION_APP"
  cp -R "$APP" "$REGION_APP"
  rm -f "$REGION_APP/Contents/Resources/${NORMAL_JAR}"
  cp "dist/${REGION_JAR}" "$REGION_APP/Contents/Resources/${REGION_JAR}"
  unzip -p "$REGION_APP/Contents/Resources/${REGION_JAR}" META-INF/MANIFEST.MF > build/region-manifest.txt
  if ! grep -q RegionLauncherApp build/region-manifest.txt; then
    echo "错误：地区版 app 内的 jar 不是 RegionLauncherApp" >&2
    exit 1
  fi
  unzip -l "$REGION_APP/Contents/Resources/${REGION_JAR}" > build/region-list.txt
  if ! grep -q 'mclite/region.lock' build/region-list.txt; then
    echo "错误：地区版 app 内的 jar 缺少 region.lock" >&2
    exit 1
  fi
  perl -0pi -e "s/${NORMAL_JAR}/${REGION_JAR}/g" "$REGION_APP/Contents/MacOS/MCLite"
  perl -0pi -e "s/MC Lite v${VERSION}/MC Lite Region v${VERSION}/g; s/com\.mclite\.launcher/com.mclite.launcher.region/g" "$REGION_APP/Contents/Info.plist"
  if command -v ditto >/dev/null 2>&1; then
    ditto -c -k --norsrc --keepParent "$REGION_APP" "dist/${REGION_MAC_ZIP}"
  fi
else
  echo "[3/4] 非 macOS，跳过应用包。"
fi

cat > run.sh <<'RUN'
#!/bin/bash
set -e
cd "$(dirname "$0")"
if [ ! -f "dist/@NORMAL_JAR@" ]; then
  ./build.sh
fi
exec java -Xms32m -Xmx256m -Dsun.java2d.opengl=false -Dsun.java2d.metal=false -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "dist/@NORMAL_JAR@" "$@"
RUN
perl -0pi -e "s/\@NORMAL_JAR\@/${NORMAL_JAR}/g" run.sh
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
if [ ! -f "dist/@REGION_JAR@" ]; then
  ./build.sh
fi
exec java -Xms32m -Xmx256m -Dsun.java2d.opengl=false -Dsun.java2d.metal=false -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "dist/@REGION_JAR@" "$@"
RUN
perl -0pi -e "s/\@REGION_JAR\@/${REGION_JAR}/g" run-region.sh
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
if not exist "dist\@REGION_JAR@" (
  echo @REGION_JAR@ not found.
  pause
  exit /b 1
)
java -Xms32m -Xmx256m -Dsun.java2d.opengl=false -Dsun.java2d.metal=false -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "dist\@REGION_JAR@" %*
BAT
perl -0pi -e "s/\@REGION_JAR\@/${REGION_JAR}/g" run-region.bat

cat > run.bat <<'BAT'
@echo off
setlocal
cd /d "%~dp0"
if not exist "dist\@NORMAL_JAR@" (
  echo @NORMAL_JAR@ not found.
  pause
  exit /b 1
)
java -Xms32m -Xmx256m -Dsun.java2d.opengl=false -Dsun.java2d.metal=false -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "dist\@NORMAL_JAR@" %*
BAT
perl -0pi -e "s/\@NORMAL_JAR\@/${NORMAL_JAR}/g" run.bat

rm -rf build/windows
mkdir -p build/windows/MC-Lite-Region
cp "dist/${REGION_JAR}" build/windows/MC-Lite-Region/
cp LICENSE NOTICE TRADEMARK.md build/windows/MC-Lite-Region/
cp packaging/windows/start.bat packaging/windows/debug.bat packaging/windows/README.txt packaging/windows/java-download.txt build/windows/MC-Lite-Region/
perl -0pi -e "s/MCLite-region\.jar/${REGION_JAR}/g" build/windows/MC-Lite-Region/start.bat build/windows/MC-Lite-Region/debug.bat
(cd build/windows && zip -rq "../../dist/${WIN_ZIP}" MC-Lite-Region)
rm -f "dist/${WIN_ZIP}.sha256"

if command -v shasum >/dev/null 2>&1; then
  (cd dist && files="${REGION_JAR}"; for z in "${REGION_MAC_ZIP}" "${WIN_ZIP}"; do [ -f "$z" ] && files="$files $z"; done; shasum -a 256 $files > SHA256SUMS.txt)
else
  (cd dist && files="${REGION_JAR}"; for z in "${REGION_MAC_ZIP}" "${WIN_ZIP}"; do [ -f "$z" ] && files="$files $z"; done; sha256sum $files > SHA256SUMS.txt)
fi

echo "[4/4] 完成。"
echo "  图形界面: ${APP}（macOS）"
echo "  macOS 压缩包: dist/${MAC_ZIP}"
echo "  通用 JAR : dist/${NORMAL_JAR}"
echo "  地区版   : dist/${REGION_JAR}"
echo "  Windows 包: dist/${WIN_ZIP}"
echo "  校验值   : dist/SHA256SUMS.txt"
if [ -x ./backup.sh ]; then
  ./backup.sh
fi
