@echo off
setlocal
cd /d "%~dp0"
if not exist "dist\MCLite.jar" (
  echo MCLite.jar not found. Build requires JDK 21+ and javac/jar on PATH.
  echo Run: build.sh on macOS/Linux, or compile with build.bat / your IDE.
  pause
  exit /b 1
)
java -Xms32m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "dist\MCLite.jar" %*
