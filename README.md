# LoginPage

A login page and an API to build things on top of. React (Vite) in the browser, Supabase Auth for
identity, a Spring Boot API (Java 21, Spring Modulith) for business logic, and a local Postgres that
holds a read-only copy of Supabase's users for that logic to use. Everything runs in Docker.

## Overview

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

## Dependencies

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

## Setup

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
