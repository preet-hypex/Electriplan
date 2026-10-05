#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

REQUIRED_MIN="22.12.0"

if ! command -v node >/dev/null 2>&1; then
  echo "run.sh: Node ${REQUIRED_MIN} or newer is needed. Install it with: brew install node@22" >&2
  exit 1
fi

version=$(node -v | sed 's/^v//')
if [ "$(printf '%s\n%s\n' "$REQUIRED_MIN" "$version" | sort -V | head -n1)" != "$REQUIRED_MIN" ]; then
  echo "run.sh: found Node ${version}, need ${REQUIRED_MIN} or newer." >&2
  exit 1
fi

if [ ! -f node_modules/.package-lock.json ] || [ package-lock.json -nt node_modules/.package-lock.json ]; then
  echo "run.sh: node_modules missing or out of date, installing from package-lock.json"
  npm ci
fi

script=${1:-dev}
if [ $# -gt 0 ]; then shift; fi
npm run "$script" -- "$@"
