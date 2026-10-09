"""Every route but health needs a valid Supabase access token, checked like the Java API checks it."""

from __future__ import annotations

import time

import pytest
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient

from app.main import app

from .tokens import auth, make_token

client = TestClient(app)

PROTECTED = [
    ("post", "/api/floorplan/analyse"),
    ("post", "/api/floorplan/calibrate"),
    ("post", "/api/floorplan/export"),
]


def call(method: str, path: str, headers: dict[str, str] | None = None):
    return getattr(client, method)(path, headers=headers or {})


def test_health_needs_no_token() -> None:
    assert client.get("/api/floorplan/health").status_code == 200


@pytest.mark.parametrize(("method", "path"), PROTECTED)
def test_every_other_route_refuses_a_request_without_a_token(method: str, path: str) -> None:
    response = call(method, path)
    assert response.status_code == 401
    assert response.headers["www-authenticate"].startswith("Bearer")


@pytest.mark.parametrize(
    "token",
    [
        pytest.param(make_token(key=ec.generate_private_key(ec.SECP256R1())), id="signed by another key"),
        pytest.param(make_token(iss="https://other.supabase.co/auth/v1"), id="another project"),
        pytest.param(make_token(aud="anon"), id="not an authenticated user"),
        pytest.param(make_token(exp=int(time.time()) - 60), id="expired"),
        pytest.param(make_token(exp=None), id="no expiry"),
        pytest.param(make_token(sub="service_role"), id="subject is not a user id"),
        pytest.param(make_token(algorithm="HS256", key="a-shared-secret-of-sufficient-length!!"), id="symmetric algorithm"),
        pytest.param("not.a.token", id="garbage"),
    ],
)
def test_a_token_that_does_not_verify_is_refused(token: str) -> None:
    response = call("post", "/api/floorplan/calibrate", auth(token))
    assert response.status_code == 401


def test_a_valid_token_gets_in() -> None:
    response = client.post(
        "/api/floorplan/calibrate", json={"pixels": 100, "millimetres": 1000}, headers=auth()
    )
    assert response.status_code == 200


def test_the_scheme_must_be_bearer() -> None:
    response = client.post(
        "/api/floorplan/calibrate",
        json={"pixels": 100, "millimetres": 1000},
        headers={"Authorization": f"Basic {make_token()}"},
    )
    assert response.status_code == 401
