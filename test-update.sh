#!/bin/bash
# 模拟一次真实更新：从当前版本更新到一个临时的高版本，检查 jar/应用包是否被原地替换。
set -euo pipefail
cd "$(dirname "$0")"

VERSION="$(cat VERSION)"
FUTURE="999.0.0"
ROOT="$(mktemp -d "${TMPDIR:-/tmp}/mclite-update-test.XXXXXX")"
cleanup() {
  chmod -R u+w "$ROOT" 2>/dev/null || true
  rm -rf "$ROOT"
}
trap cleanup EXIT

./build.sh >/dev/null

mkdir -p "$ROOT/future" "$ROOT/marker/mclite" "$ROOT/home/updates/v${FUTURE}" "$ROOT/install"
cp -R build/classes/. "$ROOT/future/"
printf 'version=%s\n' "$FUTURE" > "$ROOT/future/mclite/version.properties"
jar --create --file "$ROOT/future/normal.jar" --main-class Main -C "$ROOT/future" .
: > "$ROOT/marker/mclite/unrestricted"
jar --update --file "$ROOT/future/normal.jar" -C "$ROOT/marker" mclite/unrestricted

cp "dist/MCLite_v${VERSION}.jar" "$ROOT/install/MCLite_v${VERSION}.jar"
printf '#!/bin/bash\nexec java -jar "MCLite_v%s.jar" "$@"\n' "$VERSION" \
  > "$ROOT/install/run.sh"
chmod +x "$ROOT/install/run.sh"
cp "$ROOT/future/normal.jar" "$ROOT/home/updates/v${FUTURE}/MCLite_v${FUTURE}.jar"
cat > "$ROOT/home/updates/pending.properties" <<EOF
version=${FUTURE}
channel=normal
jar=$ROOT/home/updates/v${FUTURE}/MCLite_v${FUTURE}.jar
EOF

echo "[1/2] 普通 jar 原地更新"
(
  cd "$ROOT/install"
  JAVA_TOOL_OPTIONS=-Djava.awt.headless=true \
    java -Dmclite.home="$ROOT/home" -jar "MCLite_v${VERSION}.jar"
) >/dev/null 2>&1 || true
sleep 2
test -f "$ROOT/install/MCLite_v${FUTURE}.jar"
test ! -e "$ROOT/install/MCLite_v${VERSION}.jar"
grep -q "MCLite_v${FUTURE}.jar" "$ROOT/install/run.sh"
unzip -l "$ROOT/install/MCLite_v${FUTURE}.jar" | grep -q 'mclite/unrestricted'
if unzip -l "$ROOT/install/MCLite_v${FUTURE}.jar" | grep -q 'mclite/region.lock'; then
  echo "普通版更新后不应保留 region.lock" >&2
  exit 1
fi
test ! -e "$ROOT/home/updates/pending.properties"
test ! -e "$ROOT/home/updates/v${FUTURE}/MCLite_v${FUTURE}.jar"

if [[ "$(uname -s)" == "Darwin" ]]; then
  echo "[2/2] macOS 应用包原地更新"
  mkdir -p "$ROOT/apps" "$ROOT/home/updates/v${FUTURE}"
  cp -R "dist/MC Lite v${VERSION}.app" "$ROOT/apps/"
  cp "$ROOT/future/normal.jar" "$ROOT/home/updates/v${FUTURE}/MCLite_v${FUTURE}.jar"
  cat > "$ROOT/home/updates/pending.properties" <<EOF
version=${FUTURE}
channel=normal
jar=$ROOT/home/updates/v${FUTURE}/MCLite_v${FUTURE}.jar
EOF
  (
    cd "$ROOT/apps/MC Lite v${VERSION}.app/Contents/MacOS"
    JAVA_TOOL_OPTIONS=-Djava.awt.headless=true \
      _JAVA_OPTIONS=-Dmclite.home="$ROOT/home" ./MCLite
  ) >/dev/null 2>&1 || true
  sleep 2
  NEW_APP="$ROOT/apps/MC Lite v${FUTURE}.app"
  test -d "$NEW_APP"
  test ! -e "$ROOT/apps/MC Lite v${VERSION}.app"
  test -f "$NEW_APP/Contents/Resources/MCLite_v${FUTURE}.jar"
  grep -q "MCLite_v${FUTURE}.jar" "$NEW_APP/Contents/MacOS/MCLite"
  grep -q "<string>${FUTURE}</string>" "$NEW_APP/Contents/Info.plist"
fi

echo "UPDATE TEST PASSED (${VERSION} -> ${FUTURE})"
