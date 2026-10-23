@echo off
setlocal
cd /d "%~dp0"

where javaw >nul 2>&1
if errorlevel 1 (
  echo 没有找到 Java。
  echo 请先安装 Java 21 或更高版本：
  echo https://adoptium.net/zh-CN/temurin/releases/?version=21
  pause
  exit /b 1
)

start "" javaw -Xms32m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:-UsePerfData -XX:TieredStopAtLevel=1 -jar "MCLite-region.jar"
