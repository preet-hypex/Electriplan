#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
WEB_URL=${WEB_URL:-http://localhost:4180}
API_URL=${API_URL:-http://localhost:8081}
FLOORPLAN_URL=${FLOORPLAN_URL:-http://localhost:8082}

failures=0
pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n        %s\n' "$1" "$2"; failures=$((failures + 1)); }

fetch() {
  local url=$1 raw
  shift
  raw=$(mktemp)
  if body=$(curl -sS --max-time 10 -D "$raw" "$@" "$url" 2>&1); then
    status=$(awk 'NR == 1 { print $2 }' "$raw")
    headers=$(tr -d '\r' < "$raw" | awk -F': ' 'NR > 1 && NF > 1 { print tolower($1) ": " $2 }')
  else
    status=000
    headers=''
  fi
  rm -f "$raw"
}

header() { sed -n "s/^$1: //p" <<< "$headers" | head -1; }

echo "API  $API_URL"

fetch "$API_URL/actuator/health"
if [ "$status" = 200 ] && grep -q '"status":"UP"' <<< "$body"; then
  pass 'reports UP, so the database is reachable and every migration applied'
else
  fail 'reports UP' "GET /actuator/health returned $status: $body"
fi

fetch "$API_URL/api/me"
if [ "$status" = 401 ]; then pass '/api/me without a token is 401'; else fail '/api/me needs a token' "returned $status"; fi

fetch "$API_URL/api/me" -H 'Authorization: Bearer not-a-real-token'
if [ "$status" = 401 ]; then pass 'a token that does not verify is 401'; else fail 'bad tokens are refused' "returned $status"; fi

echo "Floor-plan analyser  $FLOORPLAN_URL"

fetch "$FLOORPLAN_URL/api/floorplan/health"
if [ "$status" = 200 ] && grep -q '"status":"ok"' <<< "$body"; then
  pass 'reports ok'
else
  fail 'reports ok' "GET /api/floorplan/health returned $status: $body"
fi

fetch "$FLOORPLAN_URL/api/floorplan/calibrate" -X POST -H 'Content-Type: application/json' -d '{"pixels":1,"millimetres":1}'
if [ "$status" = 401 ]; then pass 'analysing without a token is 401'; else fail 'the analyser needs a token' "returned $status"; fi

fetch "$FLOORPLAN_URL/api/floorplan/calibrate" -X POST -H 'Authorization: Bearer not-a-real-token' -H 'Content-Type: application/json' -d '{"pixels":1,"millimetres":1}'
if [ "$status" = 401 ]; then pass 'a token that does not verify is 401'; else fail 'bad tokens are refused by the analyser' "returned $status"; fi

echo "Web  $WEB_URL"

fetch "$WEB_URL/healthz"
if [ "$status" = 200 ]; then pass 'nginx is serving'; else fail 'nginx is serving' "GET /healthz returned $status"; fi

fetch "$WEB_URL/"
if [ "$status" = 200 ] && grep -q '<div id="root">' <<< "$body"; then
  pass 'serves the app'
else
  fail 'serves the app' "GET / returned $status"
fi
if [ "$(header cache-control)" = no-cache ]; then
  pass 'index.html is revalidated, so a new build is picked up'
else
  fail 'index.html is revalidated' "Cache-Control was '$(header cache-control)'"
fi

fetch "$WEB_URL/reset-password"
if [ "$status" = 200 ] && grep -q '<div id="root">' <<< "$body"; then
  pass 'a client-side route (/reset-password) gets the app, not a 404'
else
  fail 'client-side routes fall back to the app' "GET /reset-password returned $status"
fi

fetch "$WEB_URL/api/me"
if [ "$status" = 401 ] && [ "$(header www-authenticate | cut -c1-6)" = Bearer ]; then
  pass '/api is proxied to the API on the same origin'
else
  fail '/api is proxied' "GET /api/me through the web container returned $status"
fi

fetch "$WEB_URL/api/floorplan/health"
if [ "$status" = 200 ] && grep -q '"status":"ok"' <<< "$body"; then
  pass '/api/floorplan is proxied to the analyser, not the API'
else
  fail '/api/floorplan is proxied' "GET /api/floorplan/health through the web container returned $status"
fi

echo "DB   roles"

if who=$(docker compose -f "$ROOT/docker-compose.yml" exec -T db psql -U electriplan -d electriplan -qtA -c "select string_agg(distinct usename, ',') from pg_stat_activity where application_name like 'PostgreSQL JDBC%' or usename = 'electriplan_api'" 2>&1) && [ "$who" = electriplan_api ]; then
  pass 'the API is connected as electriplan_api (not the owner), so row-level security applies'
else
  fail 'the API connects as electriplan_api' "API database sessions are: ${who:-none}"
fi

echo "DB   electriplan.supabase_user"

if copied=$(docker compose -f "$ROOT/docker-compose.yml" exec -T db psql -U electriplan -d electriplan -qtA -c 'select count(*) from electriplan.supabase_user' 2>&1); then
  pass "the copy of Supabase's users exists ($copied rows)"
else
  fail "electriplan.supabase_user exists" "$copied"
fi

if [ "$failures" -gt 0 ]; then
  echo "$failures check(s) failed"
  exit 1
fi
echo 'all checks passed'
