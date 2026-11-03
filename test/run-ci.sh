#!/bin/bash
# Deterministic, headless regression tests. Manual/network/UI tests stay out of CI.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -d build/classes ]; then ./build.sh >/dev/null; fi
rm -rf build/test-classes
mkdir -p build/test-classes

javac --release 21 -encoding UTF-8 -cp build/classes -d build/test-classes \
  test/LocalTest.java \
  test/JvmTunerTest.java \
  test/ConfigCssTest.java \
  test/CrashTest.java

run() {
  echo "== $1"
  java -Dmclite.home="$PWD/build/test-home" \
       -cp "build/classes:build/test-classes" "mclite.$1"
}

run LocalTest
run JvmTunerTest
run ConfigCssTest
run CrashTest
echo "HEADLESS REGRESSION PASSED"
