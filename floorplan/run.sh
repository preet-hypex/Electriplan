#!/usr/bin/env bash
# Runs the floor-plan analyser outside Docker, on port 8082, creating its
# virtualenv on first use.
set -euo pipefail
cd "$(dirname "$0")"

PORT=${FLOORPLAN_PORT:-8082}

python=""
for candidate in python3.11 python3.12 python3.13 python3; do
  if command -v "$candidate" >/dev/null 2>&1 \
     && "$candidate" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)' 2>/dev/null; then
    python=$candidate
    break
  fi
done
[ -n "$python" ] || { echo "run.sh: Python 3.11 or newer is needed. brew install python@3.11" >&2; exit 1; }

command -v tesseract >/dev/null 2>&1 \
  || echo "run.sh: tesseract not found; rooms will come back unnamed. brew install tesseract" >&2

if [ ! -x .venv/bin/python ]; then
  "$python" -m venv .venv
fi
if [ ! -f .venv/.installed ] || [ requirements.txt -nt .venv/.installed ]; then
  .venv/bin/pip install -q -r requirements.txt
  touch .venv/.installed
fi

case "${1:-serve}" in
  test) shift; exec .venv/bin/python -m pytest "$@" ;;
  serve) exec .venv/bin/python -m uvicorn app.main:app --reload --port "$PORT" ;;
  *) echo "usage: run.sh [serve|test]" >&2; exit 1 ;;
esac
