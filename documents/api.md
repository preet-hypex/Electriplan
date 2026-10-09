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
| `/api/reference/addresses?q=12 glenlyon` | GET | no | Address suggestions while typing a site address (3+ characters): `[{label, street, suburb, state, postcode, latitude, longitude}]`, at most 8. From a Photon geocoder (OpenStreetMap data); `503` with a message when it is off or does not answer, and the address is typed instead |

## Projects and houses

A company's work: **projects** (a job at one site) and their **houses** (one house design each; the
database's `plan`). See [projects-workspace-plan.md](projects-workspace-plan.md). All are
company-scoped. Seeing needs `company.view`; creating, changing, archiving and restoring need
`project.edit` (owner, admin, builder).

| Endpoint | Method | Does |
|---|---|---|
| `/api/projects?q=&status=&archived=&page=&size=` | GET | A page of projects, newest activity first: `{items: [{id, reference, name, suburb, state, status, archived, houseCount, stages: [{stage, count}], lastActivityAt}], page, size, total}`. `q` searches name, reference, street and suburb; `size` 1–100 (default 25) |
| `/api/projects` | POST | Start a project (201). `name` and `site.state` required; `distributor` must supply that state; `supplyPhases` 1 or 3. The reference (`PRJ-000001`...) is assigned in order, per company |
| `/api/projects/{id}` | GET | The project with its houses |
| `/api/projects/{id}` | PUT | Change its details: send every field and the `version` you read |
| `/api/projects/{id}/archive`, `/restore` | POST | Archive (leaves the list, read-only with its houses) or restore |
| `/api/projects/{id}/houses` | POST | Add a house (201): it starts `awaiting_upload`, with its ground floor (`levels[0]`) |
| `/api/houses/recent?size=` | GET | The houses changed most recently (not archived, in projects that are not), newest first, each with its project's id, reference and name: "continue where you left off". `size` 1–20 (default 6) |
| `/api/houses/{id}` | GET | The house with its project's id, reference and name, its storeys and stage |
| `/api/houses/{id}` | PUT | Change its name or dwelling type, with its `version` |
| `/api/houses/{id}/archive`, `/restore` | POST | Archive or restore a house (not while its project is archived) |

| Response | When |
|---|---|
| `400` with `errors` | `{"message": "Check the highlighted fields.", "errors": [{"field": "site.state", "message": "Choose the state the site is in"}]}` |
| `404` | No project or house with that id **in this company**. Another company's ids are simply not found |
| `409` | The `version` sent is not the current one (someone changed it since: reload, then change again), or it is archived |

## Floor plans

A house's floor plan (its ground floor in v1): **one draft** the editor saves as it goes, and
**numbered versions** frozen from it ("Save version"). Versions never change (the database refuses);
the newest is the house's floor plan for designs. Seeing needs `company.view`; saving and restoring
need `floor-plan.edit` (owner, admin, builder, electrician). An archived house's plan is read-only (409).

| Endpoint | Method | Does |
|---|---|---|
| `/api/houses/{id}/floor-plan` | GET | What the editor opens: the draft, or the newest version: `{houseId, levelId, versionNo, state, version, basedOnVersionNo, document, savedAt, savedBy}`. `404` *"This house has no floor plan yet."* |
| `/api/houses/{id}/floor-plan/draft` | PUT | Save the plan as the draft: `{document, version?}`. `version` is the draft's from the last open or save; leave it out when there is no draft. A stale `version` is a `409`; a plan that breaks `contracts/floor-plan.schema.json` is a `400` listing each problem |
| `/api/houses/{id}/floor-plan/uploads` | POST | Multipart: `file` (JPG or PNG, up to 25 MB), `version?` (the draft's, when there is one), `mmPerPx?` (a known scale). The image is kept in the company's files (S3), the analyser reads it, and its plan becomes the draft, pointing at the kept image (`source.imageUrl` = `/api/files/{id}`). Every upload is an `analysis_run`, kept with its steps and warnings, or its reason when it fails: `422` with the analyser's reason, `503` when it or S3 is not answering, `400` when it is not a JPG or PNG |
| `/api/houses/{id}/floor-plan/versions` | POST | Save the draft as a version (201): `{version, note?}`. The next edit starts a new draft from it |
| `/api/houses/{id}/floor-plan/approve` | POST | Approve the floor plan: `{version?, note?}`. The draft (if any) is saved as a version with the note, and the house moves to `floor_plan_approved`. Only a plan being checked (`floor_plan_review`) can be approved; `409` otherwise |
| `/api/houses/{id}/floor-plan/versions` | GET | The history, newest first: number, state, note, rooms, walls, openings, floor area, open checks, when, who, and which is `current` |
| `/api/houses/{id}/floor-plan/versions/{no}` | GET | One version, to look at |
| `/api/houses/{id}/floor-plan/versions/{no}/restore` | POST | That version's contents become the draft: `{version?}` (the draft's, when there is one) |

Saving a floor plan counts as activity on the house (migration V8), so it heads "continue where you left off".

**Stages follow the work** (P7), along `plan_stage_transition` (the database refuses other moves),
each move recorded with who made it. Every floor-plan response says where the house is (`houseStage`).

| When | The house moves to |
|---|---|
| An image is uploaded | `analysing` |
| The plan changes: analysed, drawn, imported, edited, restored | `floor_plan_review` (an approved plan edited is reopened) |
| The analysis fails | back to `awaiting_upload`, or `floor_plan_review` if it has a plan |
| The floor plan is approved | `floor_plan_approved` |

Only houses in the floor-plan stages and `electrical_design` follow the floor plan; a house in review,
quoting or later stays where it is.

| Endpoint | Method | Does |
|---|---|---|
| `/api/houses/{id}/stages` | GET | The house's stage history, newest first: `[{from, to, at, by, byName, note}]` (`byName`: their name, or email) |

## Files

| Endpoint | Method | Does |
|---|---|---|
| `/api/files/{id}` | GET | A file of the company (e.g. the image behind a floor plan): its bytes and type, cacheable (a file never changes). Company-scoped, `company.view`; another company's file is a `404` |
