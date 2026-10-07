# API

Every `/api/**` call needs `Authorization: Bearer <Supabase access token>`. Any account Supabase
signs in gets through: the API has no roles, permissions or invitation list of its own.

| Endpoint | Method | Access |
|---|---|---|
| `/actuator/health` | GET | public |
| `/actuator/modulith` | GET | public, the module structure |
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
