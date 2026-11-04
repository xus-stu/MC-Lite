#!/bin/bash
# Deterministic, headless regression tests. Manual/network/UI tests stay out of CI.
set -euo pipefail
cd "$(dirname "$0")/.."

./build.sh >/dev/null
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

# Page ownership gate. Baselines are ceilings, not permission to grow.
check_page() {
  local file="src/mclite/$1.java"
  local max_total="$2"
  local max_controls="$3"
  local total controls
  total=$(grep -c 'owner\.' "$file" || true)
  controls=$(grep -Ec 'owner\.[A-Za-z0-9_]*(Toggle|Slider|Combo|Field|Area|List|Panel|Button|Label|Skin)\b' "$file" || true)
  echo "  $1 owner=$total controls=$controls (limit $max_total/$max_controls)"
  if [ "$total" -gt "$max_total" ] || [ "$controls" -gt "$max_controls" ]; then
    echo "页面所有权退化：$file" >&2
    exit 1
  fi
}
echo "== page ownership gate"
check_page InstancesPage 20 0
check_page SettingsPage 10 0
check_page LaunchPage 5 0
check_page ConfigPage 7 0

# Page classes own their controls, so LauncherFrame must not touch them.
# A stale field read here crashed 启动游戏 with a NullPointerException in 1.2.0.
echo "== frame control gate"
if ! python3 - src/mclite/LauncherFrame.java <<'PY'
import re
import sys

names = """memorySlider assetsToggle mirrorToggle jvmArgsField curseForgeKeyField
widthSlider heightSlider autoMemoryToggle autoJvmToggle metalToggle autoUpdateToggle
removeRegionLimitsToggle uiCssArea fontCombo recommendToggle gameDirField javaCombo
javaHint downloadTitle downloadSubtitle downloadTools downloadSubtabs downloadCards
downloadCardsLayout versionsTab modsTab packsTab modpacksTab shadersTab releaseFilter
snapshotFilter allFilter downloadDetailButton contentSearchField contentVersionField
contentModel contentSelectionLabel contentPageLabel contentPrevPage contentNextPage
contentInstallButton launchVersion launchAccount launchJava launchMemory launchJvm
launchAssets launchSkin configSkin configAccountLabel accountsDirLabel
forceTranslationToggle""".split()
pattern = re.compile(r"\b(" + "|".join(re.escape(n) for n in names) + r")\b")
hits = []
with open(sys.argv[1], encoding="utf-8") as source:
    for number, line in enumerate(source, 1):
        if pattern.search(line):
            hits.append(f"  {number}: {line.strip()}")
for hit in hits:
    print(hit)
sys.exit(1 if hits else 0)
PY
then
  echo "LauncherFrame 仍在直接引用页面控件" >&2
  exit 1
fi

# Bridge API smoke test: old /api and versioned /api/v1 must both answer.
BRIDGE_HOME="$PWD/build/bridge-test-home"
BRIDGE_GAME="$PWD/build/bridge-test-game"
rm -rf "$BRIDGE_HOME" "$BRIDGE_GAME"
mkdir -p "$BRIDGE_HOME" "$BRIDGE_GAME"
java -Dmclite.home="$BRIDGE_HOME" -Dmclite.gameDir="$BRIDGE_GAME" \
  -cp build/classes mclite.LauncherApp --py-bridge --bridge-port=0 \
  >"$BRIDGE_HOME/bridge.log" 2>&1 &
BRIDGE_PID=$!
trap 'kill "$BRIDGE_PID" 2>/dev/null || true' EXIT
for _ in $(seq 1 40); do
  [ -f "$BRIDGE_HOME/bridge.json" ] && break
  sleep 0.2
done
python3 - "$BRIDGE_HOME/bridge.json" <<'PYEOF'
import json, sys, urllib.request
info = json.load(open(sys.argv[1]))
for path in ("/api/health", "/api/v1/health", "/api/v1/capabilities"):
    req = urllib.request.Request(info["url"] + path,
                                 headers={"X-MCLite-Token": info["token"]})
    with urllib.request.urlopen(req, timeout=5) as response:
        assert response.status == 200, path
print("BRIDGE SMOKE PASSED")
PYEOF
echo "HEADLESS REGRESSION PASSED"
