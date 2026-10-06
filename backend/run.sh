#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

java_major() {
  local home=$1
  [ -x "$home/bin/java" ] || return 1
  "$home/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p'
}

resolve_jdk21() {
  local candidate
  if [ -n "${JAVA_HOME:-}" ] && [ "$(java_major "$JAVA_HOME" || true)" = 21 ]; then
    printf '%s\n' "$JAVA_HOME"
    return 0
  fi
  if [ -x /usr/libexec/java_home ] && candidate=$(/usr/libexec/java_home -v 21 2>/dev/null); then
    printf '%s\n' "$candidate"
    return 0
  fi
  for candidate in \
    /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
    /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
    /usr/lib/jvm/java-21-openjdk-arm64 \
    /usr/lib/jvm/java-21-openjdk-amd64
  do
    if [ "$(java_major "$candidate" || true)" = 21 ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

if ! JAVA_HOME=$(resolve_jdk21); then
  echo "run.sh: no Java 21 JDK found. Install it with: brew install openjdk@21" >&2
  echo "  Or run the API in Docker instead: docker compose up -d --build api" >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -f .env ]; then
  node ../scripts/setup.mjs sync || true
fi
if [ -f .env ]; then set -a; . ./.env; set +a; fi

command -v mvn >/dev/null 2>&1 || { echo "run.sh: Maven not found. brew install maven" >&2; exit 1; }
exec mvn spring-boot:run "$@"
