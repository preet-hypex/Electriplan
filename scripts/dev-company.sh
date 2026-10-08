#!/usr/bin/env bash
# Local development only: put a signed-in account into a company, as the
# operator will when a licence is sold (story T6). Safe to run again: the
# company and membership are created once, then left as they are.
#
#   scripts/dev-company.sh <email> "<company name>" [role] [seats]
#   scripts/dev-company.sh info@hypex.com "Hypex"            # owner, 10 seats
#
# The account must have signed in once and been copied by the API
# (electriplan.supabase_user). Runs against the docker compose database.
set -euo pipefail

EMAIL=${1:?usage: scripts/dev-company.sh <email> "<company name>" [role] [seats]}
NAME=${2:?usage: scripts/dev-company.sh <email> "<company name>" [role] [seats]}
ROLE=${3:-owner}
SEATS=${4:-10}
SLUG=$(printf '%s' "$NAME" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+|-+$//g')

cd "$(dirname "$0")/.."
docker compose exec -T db psql -U electriplan -d electriplan -v ON_ERROR_STOP=1 -q \
  -v email="$EMAIL" -v name="$NAME" -v slug="$SLUG" -v role="$ROLE" -v seats="$SEATS" <<'SQL'
\set QUIET on
SELECT id AS user_id FROM electriplan.supabase_user WHERE lower(email) = lower(:'email') \gset
\if :{?user_id}
\else
  \echo 'No account with that email yet. Sign in to the app once (the API copies new accounts), then run this again.'
  \quit
\endif
INSERT INTO electriplan.organisation (name, slug, status, seat_limit)
VALUES (:'name', :'slug', 'active', :seats)
ON CONFLICT (slug) DO NOTHING;
SELECT id AS org_id FROM electriplan.organisation WHERE slug = :'slug' \gset
INSERT INTO electriplan.organisation_member (organisation_id, user_id, role)
VALUES (:'org_id', :'user_id', :'role')
ON CONFLICT DO NOTHING;
SELECT format('%s is %s of %s (%s seats, company id %s)', :'email', m.role, o.name, o.seat_limit, o.id)
  FROM electriplan.organisation o JOIN electriplan.organisation_member m ON m.organisation_id = o.id
 WHERE o.id = :'org_id' AND m.user_id = :'user_id' \g (format=unaligned tuples_only)
SQL
