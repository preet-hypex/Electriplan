"""The analyser and the FloorPlan contract agree.

``contracts/floor-plan.schema.json`` is the source of truth for the FloorPlan;
the editor and the electrical engine are checked against it in their own
builds. Here: what the analyser really returns, and what its models write, is
valid against it; every valid example reads into the models and writes back
unchanged; and every invalid example is one the schema itself refuses.
"""

from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np
import pytest
from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator
from referencing import Registry, Resource

from app import models
from app.config import DEFAULTS, PipelineConfig
from app.main import app
from app.pipeline.orchestrator import analyse

from .tokens import auth

CONTRACTS = Path(__file__).resolve().parents[2] / "contracts"
EXAMPLES = CONTRACTS / "examples" / "floor-plan"


def _load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _validator() -> Draft202012Validator:
    # The schema refers to common.schema.json by its file name, as the other
    # contracts do; resolve it the same way.
    registry = Registry().with_resources(
        (path.name, Resource.from_contents(_load(path)))
        for path in CONTRACTS.glob("*.schema.json")
    )
    schema = _load(CONTRACTS / "floor-plan.schema.json")
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema, registry=registry, format_checker=None)


VALIDATOR = _validator()


def problems(document: object) -> list[str]:
    return [
        f"{'/'.join(map(str, error.absolute_path)) or '(root)'}: {error.message}"
        for error in VALIDATOR.iter_errors(document)
    ]


def examples(kind: str) -> list[Path]:
    return sorted((EXAMPLES / kind).glob("*.json"))


def wire(plan: models.FloorPlan) -> dict:
    """The plan as the API sends it: camelCase, absent optionals left out."""
    return json.loads(plan.model_dump_json(by_alias=True, exclude_none=True))


def test_there_are_examples_both_ways() -> None:
    assert examples("valid") and examples("invalid")


@pytest.mark.parametrize("path", examples("valid"), ids=lambda p: p.name)
def test_the_schema_accepts_every_valid_example(path: Path) -> None:
    assert problems(_load(path)) == []


@pytest.mark.parametrize("path", examples("invalid"), ids=lambda p: p.name)
def test_the_schema_refuses_every_invalid_example(path: Path) -> None:
    assert problems(_load(path)) != []


@pytest.mark.parametrize("path", examples("valid"), ids=lambda p: p.name)
def test_the_models_read_and_write_back_every_valid_example_unchanged(path: Path) -> None:
    document = _load(path)
    written = wire(models.FloorPlan.model_validate(document))
    assert problems(written) == []
    assert written == document


def test_an_empty_plan_is_valid() -> None:
    assert problems(wire(models.FloorPlan())) == []


@pytest.fixture(scope="module")
def plan_with_a_door(synthetic_plan: bytes) -> bytes:
    """The shared synthetic plan, with a door symbol drawn in Bed 1's doorway.

    Leaf at a right angle to the wall from the hinge jamb, and the quarter arc
    back to the other jamb, so the analyser reports a door with a hinge and a
    swing rather than a bare opening.
    """
    img = cv2.imdecode(np.frombuffer(synthetic_plan, np.uint8), cv2.IMREAD_COLOR)
    hinge, width, wall_y = 430, 100, 550
    cv2.line(img, (hinge, wall_y), (hinge, wall_y + width), (0, 0, 0), 2)
    cv2.ellipse(img, (hinge, wall_y), (width, width), 0, 0, 90, (0, 0, 0), 2)
    ok, buffer = cv2.imencode(".png", img)
    assert ok
    return bytes(buffer)


def test_what_the_pipeline_returns_is_valid(synthetic_plan: bytes) -> None:
    document = wire(analyse(synthetic_plan, "/api/floorplan/images/test.png").plan)
    assert document["walls"] and document["rooms"] and document["labels"] and document["source"]
    assert problems(document) == []


def test_a_detected_door_is_valid(plan_with_a_door: bytes) -> None:
    document = wire(analyse(plan_with_a_door, "/api/floorplan/images/test.png").plan)
    assert document["doors"], "the drawn door should be detected"
    assert problems(document) == []


def test_what_the_pipeline_returns_with_a_given_scale_is_valid(synthetic_plan: bytes) -> None:
    config = PipelineConfig(settings=DEFAULTS, mm_per_px_override=6.25)
    document = wire(analyse(synthetic_plan, "/api/floorplan/images/test.png", config).plan)
    assert document["source"]["scaleMethod"] == "manual"
    assert problems(document) == []


class TestTheApi:
    client = TestClient(app, headers=auth())

    def test_analyse_answers_with_a_valid_floor_plan(self, synthetic_plan: bytes) -> None:
        response = self.client.post(
            "/api/floorplan/analyse", files={"file": ("plan.png", synthetic_plan, "image/png")}
        )
        assert response.status_code == 200
        assert problems(response.json()) == []

    @pytest.mark.parametrize("path", examples("valid"), ids=lambda p: p.name)
    def test_export_writes_every_valid_example_back_valid_and_unchanged(self, path: Path) -> None:
        document = _load(path)
        response = self.client.post("/api/floorplan/export", json={"format": "json", "plan": document})
        assert response.status_code == 200
        assert problems(response.json()) == []
        assert response.json() == document

    def test_export_refuses_an_unsupported_version(self) -> None:
        document = _load(examples("valid")[0])
        document["version"] = 2
        response = self.client.post("/api/floorplan/export", json={"format": "json", "plan": document})
        assert response.status_code == 422
