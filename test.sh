#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
java -Dmclite.home="$PWD/build/test-home" -jar dist/MCLite.jar --self-test
