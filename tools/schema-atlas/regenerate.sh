#!/usr/bin/env bash
# Rebuild documents/schema-atlas.html from the Flyway migrations.
#
# Starts a throwaway Postgres 17, applies backend/src/main/resources/db/migration
# in order, loads the sample data from the schema-rules test (so tables show
# real row counts), reads the live catalogue, writes the atlas, and removes the
# container. Nothing else on your machine is touched.
#
# Requires: docker, python3.   Usage: tools/schema-atlas/regenerate.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
MIGRATIONS="$REPO/backend/src/main/resources/db/migration"
SAMPLE="$REPO/backend/src/test/resources/db/schema-rules.sql"
CTR="planna-saas-atlas"

cleanup() { docker rm -f "$CTR" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "1/4  Starting a throwaway Postgres 17 ($CTR)…"
cleanup
docker run -d --name "$CTR" -e POSTGRES_USER=app -e POSTGRES_DB=app -e POSTGRES_HOST_AUTH_METHOD=trust \
  postgres:17-alpine >/dev/null
for _ in $(seq 1 60); do
  docker exec "$CTR" pg_isready -U app -d app >/dev/null 2>&1 && break
  sleep 1
done

echo "2/4  Applying the migrations…"
{
  echo 'CREATE SCHEMA app;'
  for f in $(ls "$MIGRATIONS"/V*__*.sql | sort -t V -k2 -n); do cat "$f"; echo; done
} | docker exec -i "$CTR" psql -U app -d app -q -v ON_ERROR_STOP=1 >/dev/null

echo "3/4  Loading sample data…"
# The rules script runs as an ordinary role (row-level security applies) and,
# unlike in the test, is committed here so the atlas has rows to count.
docker exec -i "$CTR" psql -U app -d app -q -v ON_ERROR_STOP=1 >/dev/null 2>&1 <<SQL
CREATE ROLE planna_runtime NOSUPERUSER NOBYPASSRLS;
GRANT USAGE ON SCHEMA app TO planna_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA app TO planna_runtime;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA app TO planna_runtime;
SQL
{ echo 'SET ROLE planna_runtime;'; cat "$SAMPLE"; } \
  | docker exec -i "$CTR" psql -U app -d app -q -v ON_ERROR_STOP=1 >/dev/null 2>&1

echo "4/4  Building the atlas…"
export ATLAS_PSQL="docker exec $CTR psql -U app -d app"
export ATLAS_SCHEMA=app
export ATLAS_OUT_DIR="$HERE"
export ATLAS_CONTEXT="the Flyway migrations, with the schema-rules sample data"
python3 "$HERE/build_schema_atlas.py"

echo "Done → $REPO/documents/schema-atlas.html"
