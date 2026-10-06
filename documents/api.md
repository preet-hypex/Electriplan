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
