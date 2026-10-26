#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
VERSION="$(cat VERSION)"
./build.sh
rm -rf build/test-home
java -Dmclite.home="$PWD/build/test-home" -jar "dist/MCLite_v${VERSION}.jar" --self-test
java -Dmclite.home="$PWD/build/test-home" -jar "dist/MCLite_v${VERSION}-region.jar" --self-test
