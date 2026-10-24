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
sed "s#@VERSION@#${VERSION}#g; s#@ROOT@#$(pwd)#g" installer/windows.nsi > build/installer.nsi
NSISDIR="$NSIS" /tmp/nsis-install/makensis build/installer.nsi
