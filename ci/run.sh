#!/usr/bin/env bash
# GBL-013 US-5 (wave 2) — CI contract for codex-gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/codex-plugin"

job="${1:-}"
case "$job" in
  fast|test)
    ./gradlew publishToMavenLocal --no-daemon --console=plain
    ./gradlew check --no-daemon --console=plain
    ;;
  *)
    printf 'unsupported job for codex-gradle: %s\n' "${job:-<none>}" >&2
    printf 'supported: fast | test\n' >&2
    exit 2
    ;;
esac
