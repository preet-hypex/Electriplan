"""The HTTP endpoints."""

from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient

from app.main import app

from .tokens import auth

# Signed in as tokens.USER_ID; test_auth.py covers everything about tokens.
client = TestClient(app, headers=auth())


def test_health() -> None:
    assert client.get("/api/floorplan/health").json() == {"status": "ok"}


class TestAnalyse:
    def test_an_upload_returns_a_floorplan(self, synthetic_plan: bytes) -> None:
        response = client.post(
            "/api/floorplan/analyse", files={"file": ("plan.png", synthetic_plan, "image/png")}
        )
        assert response.status_code == 200
        body = response.json()
        assert body["units"] == "mm"
        assert body["walls"] and body["rooms"]
        assert body["source"]["imageUrl"].startswith("/api/floorplan/images/")
        assert body["analysis"]["wallCount"] == len(body["walls"])

    def test_the_uploaded_image_can_be_fetched_back(self, synthetic_plan: bytes) -> None:
        body = client.post(
            "/api/floorplan/analyse", files={"file": ("plan.png", synthetic_plan, "image/png")}
        ).json()
        image = client.get(body["source"]["imageUrl"])
        assert image.status_code == 200
        assert image.content == synthetic_plan

    def test_an_explicit_scale_is_honoured(self, synthetic_plan: bytes) -> None:
        response = client.post(
            "/api/floorplan/analyse",
            files={"file": ("plan.png", synthetic_plan, "image/png")},
            data={"mm_per_px": "6.25"},
        )
        assert response.json()["source"]["mmPerPx"] == 6.25

    def test_an_empty_upload_is_a_400(self) -> None:
        response = client.post("/api/floorplan/analyse", files={"file": ("plan.png", b"", "image/png")})
        assert response.status_code == 400
        assert "empty" in response.json()["detail"].lower()

    def test_an_unsupported_type_is_a_415(self) -> None:
        response = client.post(
            "/api/floorplan/analyse", files={"file": ("plan.gif", b"GIF89a-nope", "image/gif")}
        )
        assert response.status_code == 415

    def test_a_negative_scale_is_a_400(self, synthetic_plan: bytes) -> None:
        response = client.post(
            "/api/floorplan/analyse",
            files={"file": ("plan.png", synthetic_plan, "image/png")},
            data={"mm_per_px": "-1"},
        )
        assert response.status_code == 400

    def test_an_image_with_no_walls_is_a_422_with_advice(self) -> None:
        import cv2
        import numpy as np

        _, buffer = cv2.imencode(".png", np.full((600, 600, 3), 255, np.uint8))
        response = client.post(
            "/api/floorplan/analyse", files={"file": ("blank.png", bytes(buffer), "image/png")}
        )
        assert response.status_code == 422
        assert "No walls" in response.json()["detail"]

    def test_an_unknown_image_id_is_a_404(self) -> None:
        assert client.get("/api/floorplan/images/does-not-exist.png").status_code == 404

    def test_a_traversal_attempt_is_refused(self) -> None:
        assert client.get("/api/floorplan/images/..%2F..%2Fmain.py").status_code in (400, 404)


class TestCalibrate:
    def test_it_converts_a_measurement_to_a_scale(self) -> None:
        response = client.post(
            "/api/floorplan/calibrate", json={"pixels": 342, "millimetres": 3500, "current_mm_per_px": 10.0}
        )
        assert response.status_code == 200
        body = response.json()
        assert body["mm_per_px"] == pytest.approx(3500 / 342)
        assert body["factor"] == pytest.approx((3500 / 342) / 10.0)

    def test_the_factor_is_one_when_no_current_scale_is_given(self) -> None:
        body = client.post("/api/floorplan/calibrate", json={"pixels": 100, "millimetres": 1000}).json()
        assert body["factor"] == 1.0

    @pytest.mark.parametrize(
        "payload", [{"pixels": 0, "millimetres": 100}, {"pixels": 100, "millimetres": 0}]
    )
    def test_nonsense_measurements_are_refused(self, payload: dict) -> None:
        assert client.post("/api/floorplan/calibrate", json=payload).status_code == 422


class TestExport:
    def test_a_plan_round_trips_through_export(self) -> None:
        plan = {
            "version": 1,
            "units": "mm",
            "walls": [
                {
                    "id": "wall_001",
                    "start": {"x": 0, "y": 0},
                    "end": {"x": 5000, "y": 0},
                    "thickness": 110,
                }
            ],
            "rooms": [],
            "doors": [],
            "windows": [],
            "labels": [],
            "dimensions": [],
        }
        response = client.post("/api/floorplan/export", json={"format": "json", "plan": plan})
        assert response.status_code == 200
        assert "attachment" in response.headers["content-disposition"]
        returned = json.loads(response.text)
        assert returned["walls"][0]["thickness"] == 110
        assert returned["units"] == "mm"

    def test_an_unknown_format_is_refused(self) -> None:
        response = client.post("/api/floorplan/export", json={"format": "dxf", "plan": {}})
        assert response.status_code == 422  # rejected by the schema before the handler

    def test_a_malformed_plan_is_refused(self) -> None:
        response = client.post(
            "/api/floorplan/export",
            json={"format": "json", "plan": {"walls": [{"id": "w", "start": {"x": 0}}]}},
        )
        assert response.status_code == 422


def test_an_upload_survives_the_store_directory_vanishing(
    synthetic_plan: bytes, tmp_path
) -> None:
    """The upload directory is scratch space; anything may delete it."""
    import shutil

    from app.api import routes

    shutil.rmtree(routes.store.directory, ignore_errors=True)
    response = client.post(
        "/api/floorplan/analyse", files={"file": ("plan.png", synthetic_plan, "image/png")}
    )
    assert response.status_code == 200
