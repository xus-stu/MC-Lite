@echo off
setlocal
cd /d "%~dp0"
if not exist "dist\MCLite_v1.1.19.jar" (
  echo MCLite_v1.1.19.jar not found.
  pause
  exit /b 1
)
java -Xms32m -Xmx256m -Dsun.java2d.opengl=false -Dsun.java2d.metal=false -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "dist\MCLite_v1.1.19.jar" %*
