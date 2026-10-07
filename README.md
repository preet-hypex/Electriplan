# Planna SaaS

From a floor-plan image to an electrician-ready electrical design. Sign in, upload a plan, and get an
accurate, editable floor plan in millimetres; electrical layout, review and quoting follow (see
[`documents/electrical-engine-plan.md`](documents/electrical-engine-plan.md)).

| Part | Folder | Built with |
|---|---|---|
| Web app | `frontend/` | React (Vite). The floor-plan editor inside it is TypeScript + Tailwind |
| API | `backend/` | Java 21, Spring Boot, Spring Modulith, Postgres |
| Floor-plan analyser | `floorplan/` | Python 3.11, FastAPI, OpenCV, Tesseract |
| Sign-in | Supabase Auth | Hosted by Supabase; nothing to run |

Everything except Supabase runs locally in Docker. The data model is described in
[`documents/database-schema.md`](documents/database-schema.md), and drawn in
[`documents/schema-atlas.html`](documents/schema-atlas.html) (open it in a browser).

## First-time setup

About 15 minutes the first time, most of it Docker downloading images.

### 1. Install the prerequisites

| Tool | Version | Check | Install (macOS) |
|---|---|---|---|
| Docker Desktop | recent, **running** | `docker info` | https://www.docker.com/products/docker-desktop |
| Node.js | 22.12 or newer | `node -v` | `brew install node@22` |
| Git | any | `git --version` | `xcode-select --install` |

You also need a free Supabase account: https://supabase.com. Java, Maven and Python are **not**
needed to run the app; Docker builds everything. (They are only needed to run a part outside
Docker, see [Working on the code](#working-on-the-code).)

### 2. Get the code

```bash
git clone <repository-url> plannaSaas
cd plannaSaas
```

### 3. Create a Supabase project

1. At https://supabase.com/dashboard, click **New project**. Name it (e.g. `plannasaas`), pick the
   **Sydney** region, and set a database password (keep it in your password manager; this app does
   not use it). Wait until the project shows as healthy.
2. Open **Project Settings → API Keys** and check the project has a **publishable key**
   (`sb_publishable_…`) and a **secret key** (`sb_secret_…`). New projects do. If you only see the
   older *anon* and *service_role* keys, create the new ones there — setup rejects the old kind.
3. Optional but quicker: create a **personal access token** at
   https://supabase.com/dashboard/account/tokens (it starts `sbp_`). Setup uses it once to find your
   project, fetch the keys and set the redirect URLs, and never saves it. Delete it afterwards if
   you like.

Joining an existing team? Skip creating a project: ask for access to the team's Supabase project, and
use it in the next step.

### 4. Run setup

```bash
node scripts/setup.mjs
```

It asks, in order:

| Prompt | What to answer |
|---|---|
| App name and colour | Enter to keep **PlannaSaaS** and its blue, or choose your own |
| Access token | Paste the `sbp_…` token. Leave blank to paste the project URL and both keys by hand instead |
| Project | The number of your project |
| Production URL | Blank, for local use |
| Turn off public sign-ups? | **Yes** — the app has no sign-up page; accounts are by invitation |
| Apply these changes? | **Yes** — adds `http://localhost:5180/**` and `http://localhost:4180/**` as redirect URLs |
| Create an account to sign in with? | **Yes** — your email, name and a password (8+ characters) |

It writes `frontend/.env` (URL and publishable key) and `backend/.env` (URL and secret key, readable
only by you), and keeps a copy in your macOS keychain. Both `.env` files are gitignored — never
commit them. If you pasted keys by hand, also add the two redirect URLs above in Supabase under
**Authentication → URL Configuration**, or reset and invitation emails will not link back.

Check it worked: all four lines under **Files** should say `set`.

```bash
node scripts/setup.mjs status
```

### 5. Start everything

```bash
docker compose up -d --build --wait
```

This builds and starts four containers — `db` (Postgres), `api` (Java), `floorplan` (the analyser)
and `web` — and returns once all four are healthy. The first build takes several minutes.

### 6. Check the wiring

```bash
./scripts/smoke.sh
```

Every line should say `ok`, ending with `all checks passed`.

### 7. Sign in

Open **http://localhost:4180** and sign in with the account from step 4. You should see the home page
with your name, the four-step workflow, and **Floor plans** in the sidebar. Open it and try **Try the
sample plan**, or upload a JPG or PNG of a floor plan.

To add colleagues: Supabase dashboard → **Authentication → Users** → **Invite** (they get an email
and choose a password at `/invite`) or **Add user** with a password. The API copies new users into
Postgres within a few minutes.

## Day to day

| To | Run |
|---|---|
| Start, or rebuild after pulling changes | `docker compose up -d --build --wait` |
| Stop (your data is kept) | `docker compose down` |
| See what is running | `docker compose ps` |
| Follow a service's logs | `docker compose logs -f api` (or `web`, `floorplan`, `db`) |
| Rebuild just the web app after a frontend change | `docker compose up -d --build --wait web` |
| Recreate the `.env` files on the same Mac (e.g. a fresh clone) | `node scripts/setup.mjs sync` |
| Wipe the local database and uploads and start clean | `docker compose down -v` (Supabase users are untouched) |

The web app's Supabase settings are built into it, so **after re-running setup, rebuild `web`**.

## Working on the code

For hot reload, keep the API, analyser and database in Docker and run the web app with Vite:

```bash
cd frontend && npm install && npm run dev
```

Then use **http://localhost:5180**. Vite sends `/api/floorplan/*` to the analyser on 8082 and the rest
of `/api/*` to the API on 8081.

| Check | Command |
|---|---|
| Frontend tests and types | `cd frontend && npm test && npm run typecheck` |
| Analyser tests (needs Python 3.11+ and `brew install tesseract`) | `floorplan/run.sh test` |
| API tests, without installing Java | `docker run --rm -v "$PWD/backend":/src -w /src maven:3.9-eclipse-temurin-21 mvn -B test` |
| API outside Docker (needs JDK 21: `brew install openjdk@21`, and Maven) | `backend/run.sh` |
| Analyser outside Docker | `floorplan/run.sh` (port 8082) |

## Continuous integration

Every pull request into `main`, and every push to `main`, runs `.github/workflows/ci.yml` on
GitHub Actions. Its jobs run in parallel:

| Job | Checks |
|---|---|
| Frontend | `npm ci`, typecheck, tests, production build, and the setup script's tests |
| Floor-plan analyser | the Python tests, with Tesseract installed |
| API | `mvn verify`, against a real Postgres, so the Postgres tests run too |
| Docker images | `docker compose build` (the web image with placeholder Supabase settings) |
| **CI passed** | passes only if every job above passed |

Require **CI passed** before merging: GitHub → Settings → Rules → Rulesets → *New branch ruleset*,
target `main`, tick *Require a pull request before merging* and *Require status checks to pass*,
and add **CI passed**. A pull request then cannot be merged until the whole build is green.

## Troubleshooting

| You see | Cause and fix |
|---|---|
| A page saying `VITE_SUPABASE_URL … not set` | Setup has not run, or `web` was built before it. Run `node scripts/setup.mjs`, then `docker compose up -d --build --wait web` |
| `docker compose up` fails building `web` | `frontend/.env` is missing; run setup first |
| `api` never becomes healthy | Usually `SUPABASE_SECRET_KEY` is missing from `backend/.env`. Check `node scripts/setup.mjs status` and `docker compose logs api` |
| "Invalid login credentials" | The account does not exist or is unconfirmed. In Supabase → Authentication → Users, add the user and tick **Auto confirm** |
| Sign-in worked yesterday, fails today | Free Supabase projects pause after a week idle. Restore it from the dashboard |
| "Port is already allocated" | Something else uses 4180, 8081, 8082 or 5433. Stop it, or set `WEB_PORT`, `API_PORT`, `FLOORPLAN_PORT` or `DB_PORT` before `docker compose up` |
| Analysis fails with "Sign in first" or "not valid" | The analyser could not verify your sign-in. Sign out and in again; if it persists, check `docker compose logs floorplan` and that `frontend/.env` has `VITE_SUPABASE_URL`, then `docker compose up -d --build --wait floorplan` |
| Floor plans says the analyser is not answering | `docker compose ps floorplan`; restart with `docker compose up -d floorplan` |
| Rooms come back unnamed | Only outside Docker: install Tesseract (`brew install tesseract`) |
| Reset or invitation emails link to the wrong place | Add the redirect URLs from step 4 in Supabase → Authentication → URL Configuration |

## How sign-in works


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

## Floor-plan analyser

After signing in, the landing page (`/`) leads to the **floor-plan editor** (`/floor-plan`): upload a
floor-plan image and get back an editable plan, measured in millimetres. Walls, rooms, doors,
windows, room names and the scale are found from the image; the editor corrects them and saves
the plan as JSON. Both pages need a signed-in user.

It is two parts, brought across from the editor MVP:

| Part | Where | What |
|---|---|---|
| Analyser | `floorplan/` | Python 3.11, FastAPI, OpenCV and Tesseract. Every route is under `/api/floorplan` and, apart from health, needs a Supabase sign-in, verified like the Java API does (`floorplan/app/auth.py`); uploaded images are private to their uploader. Its own README-level notes live in the module docstrings; thresholds are in `floorplan/app/config.py` |
| Editor | `frontend/src/floorplan/` | TypeScript, Zustand and Tailwind, loaded only when its page opens. Tailwind's reset is scoped to the editor (`floorplan.css`), so the other pages are untouched |

The web app sends `/api/floorplan/*` to the analyser and the rest of `/api/*` to the Java API, in
Vite's proxy in development and in `frontend/nginx.conf` in Docker. The analyser keeps uploaded
images in the `floorplan-uploads` volume (`floorplan/.uploads/` outside Docker) so the editor can
show them behind the plan.

| Service | Docker | Outside Docker |
|---|---|---|
| Web | http://localhost:4180 | http://localhost:5180 (`frontend/run.sh`) |
| Java API | http://localhost:8081 | `backend/run.sh` |
| Floor-plan analyser | http://localhost:8082/docs | `floorplan/run.sh` (and `floorplan/run.sh test` for its 200 tests) |

Outside Docker the analyser needs Python 3.11+ and Tesseract (`brew install python@3.11 tesseract`);
without Tesseract it still finds walls and rooms, but rooms come back unnamed.

Next on the landing page is electrical design, planned in
[`documents/electrical-engine-plan.md`](documents/electrical-engine-plan.md).

## Setup script reference

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
