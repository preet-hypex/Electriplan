"""Access tokens for tests, signed by a key made here instead of Supabase's.

The app's verifier is pointed at this key (see conftest.py), so tests exercise
the real checks — signature, issuer, audience, expiry, subject — without a
network call to Supabase.
"""

from __future__ import annotations

import time

import jwt
from cryptography.hazmat.primitives.asymmetric import ec

SUPABASE_URL = "https://test-project.supabase.co"
ISSUER = f"{SUPABASE_URL}/auth/v1"
USER_ID = "3ea31732-b4c7-4ee6-8743-ab184261bd6c"
OTHER_USER_ID = "9b0f3c2e-1d4a-4c8b-9e7f-2a6d5c4b3a21"

SIGNING_KEY = ec.generate_private_key(ec.SECP256R1())
PUBLIC_KEY = SIGNING_KEY.public_key()


def make_token(key=SIGNING_KEY, algorithm: str = "ES256", **overrides) -> str:
    """A Supabase-shaped access token; pass a claim as None to leave it out."""
    now = int(time.time())
    claims = {
        "iss": ISSUER,
        "aud": "authenticated",
        "sub": USER_ID,
        "exp": now + 3600,
        "iat": now,
        "role": "authenticated",
    }
    claims.update(overrides)
    claims = {k: v for k, v in claims.items() if v is not None}
    return jwt.encode(claims, key, algorithm=algorithm)


def auth(token: str | None = None) -> dict[str, str]:
    return {"Authorization": f"Bearer {token or make_token()}"}
