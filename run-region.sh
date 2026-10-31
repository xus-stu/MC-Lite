#!/bin/bash
set -e
cd "$(dirname "$0")"
if [ ! -f "dist/MCLite_v1.1.63-region.jar" ]; then
  ./build.sh
fi
exec java -Xms16m -Xmx192m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=64m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=64m -XX:-UsePerfData -jar "dist/MCLite_v1.1.63-region.jar" "$@"
