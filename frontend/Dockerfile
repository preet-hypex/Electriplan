# syntax=docker/dockerfile:1

FROM node:22-alpine AS build
WORKDIR /src

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

RUN node --input-type=module -e "\
  import { loadEnv } from 'vite'; \
  const env = loadEnv('production', process.cwd(), 'VITE_'); \
  const missing = ['VITE_SUPABASE_URL', 'VITE_SUPABASE_PUBLISHABLE_KEY'].filter(k => !env[k]); \
  if (missing.length) { \
    console.error('frontend/.env does not set ' + missing.join(' or ') + '. Run node scripts/setup.mjs from the repository root.'); \
    process.exit(1); \
  }"

RUN VITE_API_URL= npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.d/40-app-env.sh /docker-entrypoint.d/40-app-env.sh
COPY --from=build /src/dist /usr/share/nginx/html
COPY --from=build --chown=101:101 /src/dist/env.js /usr/share/nginx/html/env.js

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz >/dev/null || exit 1
