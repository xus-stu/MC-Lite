#!/bin/bash
set -e
cd "$(dirname "$0")"
VERSION="$(cat VERSION)"
if ! command -v makensis >/dev/null 2>&1 && [ ! -x /tmp/nsis-install/makensis ]; then
  echo "需要 makensis 或 /tmp/nsis-install/makensis"
  exit 1
fi
NSIS="${NSISDIR:-/tmp/nsis-win/nsis-3.10}"
rm -rf build/installer
mkdir -p build/installer/MC-Lite
cp "dist/MCLite_v${VERSION}.jar" build/installer/MC-Lite/
cp -R /tmp/sfxtest2/MC-Lite/runtime build/installer/MC-Lite/runtime
cp LICENSE NOTICE TRADEMARK.md build/installer/MC-Lite/
cat > build/installer/MC-Lite/start.bat <<BAT
@echo off
setlocal
cd /d "%~dp0"
if not exist "runtime\bin\javaw.exe" (
  echo Missing Java runtime.
  pause
  exit /b 1
)
"runtime\bin\javaw.exe" -Xms32m -Xmx256m -Dsun.java2d.opengl=false -Dsun.java2d.metal=false -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "MCLite_v${VERSION}.jar"
BAT
sed "s#@VERSION@#${VERSION}#g; s#@ROOT@#$(pwd)#g" installer/windows.nsi > build/installer.nsi
NSISDIR="$NSIS" /tmp/nsis-install/makensis build/installer.nsi
