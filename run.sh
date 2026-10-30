#!/bin/bash
set -e
cd "$(dirname "$0")"
if [ ! -f "dist/MCLite_v1.1.47.jar" ]; then
  ./build.sh
fi
exec java -Xms32m -Xmx256m -Dsun.java2d.opengl=false -Dsun.java2d.metal=false -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "dist/MCLite_v1.1.47.jar" "$@"
