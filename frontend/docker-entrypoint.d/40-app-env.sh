#!/bin/sh
set -eu

target=/usr/share/nginx/html/env.js
esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }

if [ -n "${VITE_SUPABASE_URL:-}" ] && [ -n "${VITE_SUPABASE_PUBLISHABLE_KEY:-}" ]; then
  printf 'window.__APP_ENV__ = { VITE_SUPABASE_URL: "%s", VITE_SUPABASE_PUBLISHABLE_KEY: "%s" };\n' \
    "$(esc "$VITE_SUPABASE_URL")" "$(esc "$VITE_SUPABASE_PUBLISHABLE_KEY")" > "$target"
  echo "web: /env.js written from VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY"
else
  echo "web: using the Supabase settings built into the bundle from frontend/.env"
fi
