@echo off
setlocal
cd /d "%~dp0"
if not exist "dist\MCLite_v1.1.65-region.jar" (
  echo MCLite_v1.1.65-region.jar not found.
  pause
  exit /b 1
)
java -Xms16m -Xmx192m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=64m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=64m -XX:-UsePerfData -jar "dist\MCLite_v1.1.65-region.jar" %*
