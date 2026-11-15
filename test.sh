#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
VERSION="$(cat VERSION)"
./build.sh
rm -rf build/test-home
java -Dmclite.home="$PWD/build/test-home" \
  -cp "dist/MCLite_v${VERSION}.jar:dist/MCLiteTests_v${VERSION}.jar" mclite.SelfTestMain
java -Dmclite.home="$PWD/build/test-home" \
  -cp "dist/MCLite_v${VERSION}-region.jar:dist/MCLiteTests_v${VERSION}.jar" mclite.SelfTestMain
./test/run-ci.sh
