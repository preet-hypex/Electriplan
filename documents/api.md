# API

Every `/api/**` call needs `Authorization: Bearer <Supabase access token>`. Any account Supabase
signs in gets through: the API has no roles, permissions or invitation list of its own.

| Endpoint | Method | Access |
|---|---|---|
| `/actuator/health` | GET | public |
| `/actuator/modulith` | GET | public, the module structure |
| `/api/me` | GET | any Supabase account: the token's `id`, `email` and `tokenExpiresAt`, and `copy`, this user's row in `app.supabase_user` (`null` until it has been copied) |

The first call from a user the copy does not have yet copies them from Supabase before the endpoint
runs, so `copy` is normally filled in on the first call.

## Responses

- `401` the token is missing, expired, or does not verify against the project's JWKS.
- `403` the path is outside `/api/**` and the public actuator endpoints.

`id` is always the Supabase user id: the token's `sub` and `app.supabase_user.id`.

## Floor-plan analyser

Served by the Python service in `floorplan/`, reached through the web app's origin. It does not
check tokens yet: the pages that use it need a signed-in user, but the endpoints themselves are
open to anyone who can reach the web app.

| Endpoint | Method | Does |
|---|---|---|
| `/api/floorplan/health` | GET | `{"status": "ok"}` |
| `/api/floorplan/analyse` | POST | `multipart/form-data`: `file` (JPG/PNG, ≤ 25 MB), optional `mm_per_px`. Returns a FloorPlan |
| `/api/floorplan/calibrate` | POST | `{ pixels, millimetres, current_mm_per_px? }` → `{ mm_per_px, factor, confidence }` |
| `/api/floorplan/export` | POST | `{ format: "json", plan }` → the validated plan as a download |
| `/api/floorplan/images/{id}` | GET | An uploaded image, for the editor's reference layer |

Interactive docs: http://localhost:8082/docs.
