# LoginPage

A login page and an API to build things on top of. React (Vite) in the browser, Supabase Auth for
identity, a Spring Boot API (Java 21, Spring Modulith) for business logic, and a local Postgres that
holds a read-only copy of Supabase's users for that logic to use. Everything runs in Docker.

## How authentication works

**Supabase is the source of truth for authentication.** Signing in, passwords, password resets,
invitations, email confirmation and banning all happen in Supabase. The API never handles a
password, never sends an email and keeps no roles, permissions or account state of its own.

```
                 1. sign in, reset password, accept invitation
   ┌──────────┐ ─────────────────────────────────────────► ┌───────────────────┐
   │  React   │ ◄───────────────────────────────────────── │   Supabase Auth   │
   │ (browser)│         2. access token (ES256 JWT)        │ (source of truth) │
   └──────────┘                                            └───────────────────┘
        │                                                     ▲             ▲
        │ 3. Authorization: Bearer <token>                    │ 4. JWKS     │ 5. Admin API
        ▼                                                     │             │
   ┌──────────┐ ──────────────────────────────────────────────┘             │
   │  Java    │ ────────────────────────────────────────────────────────────┘
   │   API    │   4. verify each token's signature, issuer and audience
   └──────────┘   5. list and read users with the secret key
        │ 6. JDBC
        ▼
   ┌────────────────────┐
   │ Postgres (local)   │   localhost:5433, in Docker.
   │ app.supabase_user  │   A read-only copy of Supabase's users.
   └────────────────────┘   The browser has no privileges here.
```

1. The React app talks to Supabase directly with `@supabase/supabase-js`: sign-in, the "Forgot?"
   reset link, accepting an invitation, choosing a new password. Supabase sends every email.
2. Supabase issues a JWT signed with the project's asymmetric key. It carries identity only.
3. Calls to the API send `Authorization: Bearer <token>` (`frontend/src/lib/api.js`).
4. The `security` module verifies the token against the project's public **JWKS** endpoint
   (ES256 / RS256) and checks the issuer, the `authenticated` audience and that `sub` is a Supabase
   user id. **Any valid Supabase token gets in.** There is no invitation gate, role or deactivation
   check in the API; to stop someone signing in, ban or delete them in Supabase, and to stop
   strangers signing up, turn off sign-ups in Supabase (setup offers to).
5. The `users` module copies Supabase's users into Postgres with the secret key (see below).
6. The API reaches Postgres over JDBC. The browser never does.

## The copy of Supabase's users

`app.supabase_user` mirrors Supabase's `auth.users`: id, email, phone, `user_metadata`,
`app_metadata`, email confirmation, invitation, last sign-in, ban and created/updated times, plus
`copied_at`. The id is the Supabase user id, which is also the token's `sub`.

- **Every 5 minutes** (`USER_SYNC_INTERVAL`, and once at start-up) the API lists every user through
  Supabase's Admin API and writes the ones that changed. A user missing from the listing is looked
  up on its own and removed from the copy only when Supabase answers that it no longer exists, so a
  failed or shifting listing never deletes anyone.
- **On the first API call** from someone the copy does not have yet, the API copies that one user
  straight away, so whoever is calling always has a row.
- Nothing else writes to the table. Don't edit it by hand, and don't add columns for your own data:
  put business data in your own tables and point them at `app.supabase_user(id)`.

In Java, `SupabaseUsers.find(id)` reads the copy, and `AuthenticatedUsers.current()` gives the
caller's id. `SupabaseUsers.copy(id)` refreshes one user on demand.

## Quick start

Needs Docker and Node 22.12+. Java is only needed to run the API outside Docker.

```bash
node scripts/setup.mjs                       # name, colour theme, Supabase keys -> frontend/.env, backend/.env
docker compose up -d --build --wait          # Postgres, API and web
./scripts/smoke.sh                           # read-only checks that the stack is wired up
```

Then open **http://localhost:4180** and sign in. Setup offers to create an account in Supabase for
you; add more people in the Supabase dashboard under **Authentication → Users** (invite, or add with
a password). Invitation and reset emails come from Supabase and link back to `/invite` and
`/reset-password`.

For hot reload, run the frontend with Vite against the API in Docker:

```bash
cd frontend && npm run dev                   # http://localhost:5180, /api proxied to localhost:8081
```

After changing backend code, `docker compose up -d --build api`, or stop that container and run
`backend/run.sh` (JDK 21) on the same port.

### Ports

PlannaOne's stack uses 5173, 4173, 8080 and 5432 on this machine, so this one stays out of its way.
Supabase's redirect allow list holds the two web origins, so keep them fixed.

| Port | What | Set by |
|---|---|---|
| 5180 | Vite dev server (`npm run dev`) | `frontend/ports.mjs` |
| 4180 | web container (nginx: the built app, `/api` proxied) and `vite preview` | `WEB_PORT`, `frontend/ports.mjs` |
| 8081 | API | `API_PORT` |
| 5433 | Postgres (`psql -h localhost -p 5433 -U app app`) | `DB_PORT` |

All of them are bound to 127.0.0.1. The database trusts local connections, as PlannaOne's does;
it is for development, not for exposing.

## The setup script

`scripts/setup.mjs` has no dependencies and is safe to re-run.

```bash
node scripts/setup.mjs                    # everything below, in order
node scripts/setup.mjs brand              # app name and colour theme
node scripts/setup.mjs supabase           # project URL, keys and auth settings
node scripts/setup.mjs sync               # fresh clone: rebuild both .env files from the keychain
node scripts/setup.mjs status             # what is configured, and how many users are copied
node scripts/setup.mjs forget <KEY|all>
```

- **Brand.** It asks for a name and a colour and generates every themed token in
  `frontend/src/styles/theme.css` (contrast-checked, so a light colour gets dark button text) and
  the favicon. The sign-in copy is in `frontend/src/brand.json`.
- **Supabase.** With a personal access token it lists your projects, fetches the URL and the
  publishable and secret keys, adds `http://localhost:5180/**` and `http://localhost:4180/**` (and
  a production URL if you give one) to the redirect allow list, and offers to turn off public
  sign-ups. It shows each change and asks first. The token is never stored. The URL and keys go in
  the macOS keychain and in `frontend/.env` (the publishable key only) and `backend/.env` (the
  secret key, mode 600). It then offers to create an account to sign in with.

The API refuses to start without `SUPABASE_SECRET_KEY`, since it copies the users with it.

## Layout

```
frontend/                React app (see below), Dockerfile, nginx.conf
backend/                 Spring Boot API, Dockerfile, .env.example, run.sh
  src/main/java/loginpage/
    security/            JWT verification against Supabase's JWKS, the current caller
    users/               the copy of Supabase's users: sync, Admin API client, /api/me
  src/main/resources/db/migration/   Flyway: V1 app.supabase_user
scripts/setup.mjs        brand, Supabase environment, keychain, first account
scripts/smoke.sh         read-only checks against a running stack
docker-compose.yml       db, api, web
documents/api.md         every endpoint
```

Each top-level package under `loginpage` is a Spring Modulith module; `ModularityTests` fails the
build if one reaches into another's internals. `security` depends on no other module; `users`
depends on `security`. Add business logic as new modules beside them. Data access is plain SQL
through `JdbcClient` (no JPA).

In the frontend, `context/AuthContext.jsx` wraps Supabase Auth, `components/ProtectedRoute.jsx`
sends signed-out visitors to sign in, and `lib/api.js` adds the Bearer token to API calls. The pages
are Login, Invite (choose a name and password), ResetPassword and Home, which shows your Supabase
session next to the API's copy of you from `/api/me`.

## Tests

```bash
cd frontend && npm test                         # Vitest: pages, routing, api client, palette
node --test scripts/lib/                        # setup script helpers
cd backend && mvn test                          # JDK 21: token checks, sync rules, Admin API client (no Supabase needed)
APP_TEST_DB_URL=jdbc:postgresql://localhost:5433/app mvn test   # adds the *PostgresTests: real migration and SQL
./scripts/smoke.sh                              # against the running stack
```

Without a local JDK 21 the backend tests run in the same image the Dockerfile builds with:

```bash
docker run --rm --network loginpage_default -e APP_TEST_DB_URL=jdbc:postgresql://db:5432/app \
  -v "$PWD/backend":/src -v "$HOME/.m2":/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn test
```
