"""Shared fixtures.

The pipeline tests run against a floor plan this file *draws*, not against a
stored image or stored coordinates. That keeps the suite honest: it exercises
the same detection code an upload would, and it cannot accidentally encode the
answer for one particular plan.
"""

from __future__ import annotations

import cv2
import numpy as np
import pytest

from app.auth import TokenVerifier, get_verifier
from app.main import app

from .tokens import PUBLIC_KEY, SUPABASE_URL


@pytest.fixture(autouse=True, scope="session")
def verify_with_the_test_key():
    """Every test's tokens are checked against the test signing key, not Supabase."""
    app.dependency_overrides[get_verifier] = lambda: TokenVerifier(
        SUPABASE_URL, key_for=lambda _token: PUBLIC_KEY
    )
    yield
    app.dependency_overrides.pop(get_verifier, None)


@pytest.fixture(scope="session")
def synthetic_plan() -> bytes:
    """A small two-bedroom plan rendered the way an estate agent's would be.

    Thick black walls, a door gap in each bedroom, room names and one dimension
    string, a border rule and a disclaimer line outside the drawing.
    """
    # Drawn at exactly 5 mm per pixel, so the dimension string written on the
    # plan and the geometry agree — the scale test checks the pipeline recovers
    # that number.
    width, height = 1400, 1100
    img = np.full((height, width, 3), 255, np.uint8)

    # Page border rule and page furniture, so region detection has something
    # to strip.
    cv2.rectangle(img, (20, 20), (width - 20, height - 20), (0, 0, 0), 3)
    cv2.putText(img, "12 EXAMPLE STREET", (500, 80), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 0), 2)
    cv2.putText(
        img,
        "DISCLAIMER: THIS PLAN IS A VISUAL GUIDE ONLY",
        (120, 1040),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.5,
        (0, 0, 0),
        1,
    )

    ext, internal = 16, 10
    black = (0, 0, 0)

    def line(p1: tuple[int, int], p2: tuple[int, int], thickness: int) -> None:
        cv2.line(img, p1, p2, black, thickness)

    # Outer shell: 1000 x 800 px = 5.0 m x 4.0 m.
    line((200, 150), (1200, 150), ext)
    line((1200, 150), (1200, 950), ext)
    line((1200, 950), (200, 950), ext)
    line((200, 950), (200, 150), ext)

    # Corridor wall with a doorway into each bedroom.
    line((200, 550), (430, 550), internal)
    line((530, 550), (760, 550), internal)
    line((860, 550), (1200, 550), internal)

    # Bedroom divider.
    line((700, 150), (700, 550), internal)

    # Room names, and a dimension string for the living room — 1000 x 400 px
    # of shell, so 5.0 m x 2.0 m at the drawing's scale.
    font = cv2.FONT_HERSHEY_SIMPLEX
    cv2.putText(img, "BED 1", (330, 350), font, 1.0, black, 2)
    cv2.putText(img, "BED 2", (860, 350), font, 1.0, black, 2)
    cv2.putText(img, "LIVING", (600, 720), font, 1.0, black, 2)
    cv2.putText(img, "5.0m X 2.0m", (540, 790), font, 0.7, black, 2)

    ok, buffer = cv2.imencode(".png", img)
    assert ok
    return bytes(buffer)
