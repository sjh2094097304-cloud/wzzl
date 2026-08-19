#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle 未安装  请使用 Android Studio 或 GitHub Actions" >&2
exit 1
