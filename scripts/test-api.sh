#!/usr/bin/env bash
# Runs the API's tests against a throwaway Postgres, the way CI does, so the
# Postgres tests run instead of being skipped. The database lives only for the
# run: it is removed afterwards, pass or fail, and never touches the dev
# database in docker-compose.
#
#   ./scripts/test-api.sh                     everything (mvn verify)
#   ./scripts/test-api.sh -Dtest=ModularityTests   any extra Maven arguments
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
NAME="electriplan-test-db-$$"
API_PASSWORD=electriplan_api

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

echo "Starting a throwaway Postgres ($NAME)"
# Same image, database and user as CI and docker-compose. Docker picks a free
# local port, so this never clashes with a database already running.
docker run -d --rm --name "$NAME" \
  -e POSTGRES_DB=electriplan -e POSTGRES_USER=electriplan -e POSTGRES_PASSWORD=electriplan \
  -p 127.0.0.1::5432 postgres:17-alpine >/dev/null

for _ in $(seq 1 60); do
  # pg_isready over TCP: the image's first-start init only listens on a socket.
  if docker exec "$NAME" pg_isready -q -h 127.0.0.1 -U electriplan -d electriplan; then break; fi
  sleep 0.5
done
docker exec "$NAME" pg_isready -q -h 127.0.0.1 -U electriplan -d electriplan \
  || { echo "Postgres did not start" >&2; exit 1; }
PORT=$(docker port "$NAME" 5432/tcp | head -n1 | awk -F: '{ print $NF }')

# The API's own login, as CI's "Create the API's database login" step does.
docker exec -i "$NAME" psql -q -U electriplan -d electriplan -v api_password="$API_PASSWORD" \
  < "$ROOT/infra/postgres/application-login.sql"

export APP_TEST_DB_URL="jdbc:postgresql://localhost:$PORT/electriplan"
export APP_TEST_DB_USER=electriplan APP_TEST_DB_PASSWORD=electriplan
export APP_TEST_DB_API_USER=electriplan_api APP_TEST_DB_API_PASSWORD="$API_PASSWORD"

echo "Running the API tests against $APP_TEST_DB_URL"
cd "$ROOT/backend"
mvn --batch-mode --no-transfer-progress verify "$@"
