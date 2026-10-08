# API

Every `/api/**` call needs `Authorization: Bearer <Supabase access token>`. What a signed-in person
may then do depends on their role in the company the request acts in (see [Companies](#companies)).

**The full, current description is the API's own OpenAPI document**, which springdoc builds from the
code:

| | Where |
|---|---|
| Swagger UI (try calls with your access token: **Authorize**, paste it) | **http://localhost:4180/api/docs** (or `:5180` with Vite, `:8081` direct) |
| OpenAPI document | `/api/docs/openapi.json`, committed as [`contracts/openapi.json`](../contracts/openapi.json) |
| TypeScript types for the web app | `frontend/src/api/schema.ts` (generated), used through `frontend/src/api/index.ts` (`apiGet`) |

Both are open without a sign-in (each endpoint still needs one); `API_DOCS_ENABLED=false` turns them
off. Company-scoped endpoints list the `X-Organisation-Id` header and their 400/403 answers, and an
endpoint that needs a permission says which and the roles that have it (also as `x-permission` and
`x-roles`). The floor-plan analyser is a separate service with its own description (FastAPI's
`/docs` on port 8082). This page explains the rules; the tables below are a summary.

| Endpoint | Method | Access |
|---|---|---|
| `/actuator/health` | GET | public |
| `/actuator/modulith` | GET | public, the module structure |
| `/api/docs`, `/api/docs/openapi.json` | GET | public, this API's description |
| `/api/me` | GET | any Supabase account: the token's `id`, `email` and `tokenExpiresAt`, and `copy`, this user's row in `electriplan.supabase_user` (`null` until it has been copied) |

The first call from a user the copy does not have yet copies them from Supabase before the endpoint
runs, so `copy` is normally filled in on the first call.

## Responses

- `401` the token is missing, expired, or does not verify against the project's JWKS.
- `403` the path is outside `/api/**` and the public actuator endpoints.

`id` is always the Supabase user id: the token's `sub` and `electriplan.supabase_user.id`.

## Floor-plan analyser

Served by the Python service in `floorplan/`, reached through the web app's origin. Every endpoint
except health needs `Authorization: Bearer <Supabase access token>`, checked exactly as the Java
API checks it (`floorplan/app/auth.py`): signed by the project's key (ES256/RS256, from its public
JWKS endpoint), issued by `<SUPABASE_URL>/auth/v1`, for the `authenticated` audience, not expired,
and `sub` a Supabase user id. The analyser needs only the public `SUPABASE_URL`, read from
`frontend/.env`; it never sees the secret key.

Uploaded images belong to the user who uploaded them: they are stored per user, and another user
asking for one gets a 404. Images uploaded before this rule have no owner and are no longer served;
the plans that point at them still open, without the image behind them.

| Endpoint | Method | Does |
|---|---|---|
| `/api/floorplan/health` | GET | `{"status": "ok"}`. Public, for health checks |
| `/api/floorplan/analyse` | POST | `multipart/form-data`: `file` (JPG/PNG, ≤ 25 MB), optional `mm_per_px`. Returns a FloorPlan |
| `/api/floorplan/calibrate` | POST | `{ pixels, millimetres, current_mm_per_px? }` → `{ mm_per_px, factor, confidence }` |
| `/api/floorplan/export` | POST | `{ format: "json", plan }` → the validated plan as a download |
| `/api/floorplan/images/{id}` | GET | One of the caller's uploaded images, for the editor's reference layer. Another user's image is a 404 |

Interactive docs: http://localhost:8082/docs.

Without a token, or with one that does not verify, every endpoint but health answers `401` with
`WWW-Authenticate: Bearer`. If `SUPABASE_URL` is not set, it answers `503` saying so.

## Companies

Most Electriplan endpoints work inside one company. They are marked `@CompanyScoped` in the code and
say so here. For those:

- Send **`X-Organisation-Id: <company id>`**. A person who belongs to exactly one company may leave
  it out.
- The caller must be an **active member** of that company, and the company must not be closed.
- Every database query the endpoint makes is then limited to that company by row-level security.

| Response | When |
|---|---|
| `400` | The header is not a company id, or it is missing and the caller belongs to several companies |
| `403` | The caller is not an active member of that company, the company is closed or does not exist (one message for all three, so nothing is learned about other companies), or the caller belongs to no company |
| `403` | The caller's role lacks the endpoint's permission. The message names it and the roles that have it: *"As a builder you cannot do this (licence.view). Roles that can: owner, admin."* |

**Permissions.** An endpoint that needs one says so below (`@RequiresPermission` in the code). Which
role has which is in the table in
[teams-and-licences-plan.md §4](teams-and-licences-plan.md#4-roles-and-permissions), generated from
`PermissionMatrix`.

Errors are `{"message": "..."}`.

| Endpoint | Method | Company-scoped | Returns |
|---|---|:-:|---|
| `/api/organisations` | GET | no | The caller's companies, for the company switcher: `[{id, name, role, licence}]` — active memberships of companies that are not closed |
| `/api/organisations/current` | GET | **yes** | The company the request acts in, the caller's role and what it may do: `{id, name, slug, role, licence, permissions: ["company.view", ...]}`. The web app shows only actions in `permissions` |
| `/api/organisations/current/licence` | GET | **yes**, needs `licence.view` | The licence and seat use: `{status, seatLimit, seatsInUse, licenceStartsOn, licenceEndsOn}` |
| `/api/reference/distributors?state=VIC` | GET | no | Electricity distributors: `[{code, name, state}]` |
