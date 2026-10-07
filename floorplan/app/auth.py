"""Who is calling: Supabase access tokens, verified the way the Java API does.

Every route except the health check needs ``Authorization: Bearer <token>``,
where the token is the access token Supabase gave the browser at sign-in. It is
checked exactly as ``SupabaseJwtDecoderConfiguration`` checks it in the Java
API, so one sign-in works the same everywhere:

- signed by the project's key (ES256 or RS256), fetched from its public JWKS
  endpoint, ``<SUPABASE_URL>/auth/v1/.well-known/jwks.json``;
- issued by ``<SUPABASE_URL>/auth/v1``;
- for the ``authenticated`` audience;
- not expired;
- ``sub`` is a Supabase user id (a UUID).

The analyser holds no secret: the JWKS endpoint is public, and only
SUPABASE_URL is needed (VITE_SUPABASE_URL is accepted too, so the web app's
public .env is enough).
"""

from __future__ import annotations

import logging
import os
import uuid
from dataclasses import dataclass
from functools import lru_cache
from typing import Callable

import jwt
from fastapi import Depends, HTTPException, Request

log = logging.getLogger("floorplan.auth")

ALGORITHMS = ["ES256", "RS256"]
AUDIENCE = "authenticated"


@dataclass(frozen=True)
class User:
    """The signed-in caller. ``id`` is the Supabase user id."""

    id: str


def _unauthorised(reason: str) -> HTTPException:
    return HTTPException(
        status_code=401,
        detail=reason,
        headers={"WWW-Authenticate": 'Bearer error="invalid_token"'},
    )


class TokenVerifier:
    """Verifies Supabase access tokens for one project.

    ``key_for`` finds the public key a token was signed with. In production it
    is a JWKS client that fetches and caches the project's keys; tests pass
    their own.
    """

    def __init__(self, supabase_url: str, key_for: Callable[[str], object] | None = None) -> None:
        base = supabase_url.rstrip("/")
        self.issuer = f"{base}/auth/v1"
        self.jwks_uri = f"{self.issuer}/.well-known/jwks.json"
        if key_for is None:
            client = jwt.PyJWKClient(self.jwks_uri, cache_keys=True, lifespan=3600)
            key_for = lambda token: client.get_signing_key_from_jwt(token).key  # noqa: E731
        self._key_for = key_for

    def verify(self, token: str) -> User:
        try:
            key = self._key_for(token)
            claims = jwt.decode(
                token,
                key,
                algorithms=ALGORITHMS,
                audience=AUDIENCE,
                issuer=self.issuer,
                options={"require": ["exp", "iss", "aud", "sub"]},
            )
        except jwt.ExpiredSignatureError as exc:
            raise _unauthorised("Your session has ended. Sign in again.") from exc
        except jwt.PyJWKClientError as exc:
            # Either the token names a key the project does not have, or the
            # JWKS endpoint could not be reached. Both mean "cannot verify".
            log.warning("Could not find the key for a token: %s", exc)
            raise _unauthorised("The access token could not be verified.") from exc
        except jwt.InvalidTokenError as exc:
            raise _unauthorised("The access token is not valid.") from exc

        try:
            uuid.UUID(str(claims["sub"]))
        except ValueError as exc:
            raise _unauthorised("The access token does not name a Supabase user.") from exc
        return User(id=str(uuid.UUID(claims["sub"])))


def supabase_url() -> str | None:
    return os.environ.get("SUPABASE_URL") or os.environ.get("VITE_SUPABASE_URL") or None


@lru_cache(maxsize=1)
def get_verifier() -> TokenVerifier:
    """The verifier for this deployment's project. Overridden in tests."""
    url = supabase_url()
    if not url:
        raise HTTPException(
            status_code=503,
            detail=(
                "The floor-plan analyser cannot check sign-ins: SUPABASE_URL is not set. "
                "Run node scripts/setup.mjs, then restart the analyser."
            ),
        )
    return TokenVerifier(url)


def current_user(request: Request, verifier: TokenVerifier = Depends(get_verifier)) -> User:
    """FastAPI dependency: the caller, or a 401."""
    header = request.headers.get("Authorization", "")
    scheme, _, token = header.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise HTTPException(
            status_code=401,
            detail="Sign in first: this request has no access token.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return verifier.verify(token.strip())
