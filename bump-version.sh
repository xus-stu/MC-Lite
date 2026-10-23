#!/bin/bash
set -e
cd "$(dirname "$0")"
current="$(cat VERSION)"
IFS=. read -r major minor patch <<< "$current"
case "${1:-patch}" in
  major)
    minor=$((minor + 1))
    patch=$((patch + 5))
    ;;
  patch)
    patch=$((patch + 1))
    ;;
  *)
    echo "用法: ./bump-version.sh patch|major"
    exit 1
    ;;
esac
next="$major.$minor.$patch"
printf '%s\n' "$next" > VERSION
echo "$current -> $next"
