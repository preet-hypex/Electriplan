"""End-to-end pipeline and FloorPlan serialisation."""

from __future__ import annotations

import json

import pytest

from app import models
from app.config import PipelineConfig
from app.pipeline import ocr
from app.pipeline.orchestrator import AnalysisError, analyse
from app.pipeline.preprocess import ImageLoadError, load_image


class TestPipeline:
    def test_a_drawn_plan_comes_back_as_walls_rooms_and_labels(self, synthetic_plan: bytes) -> None:
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan

        assert plan.units == "mm"
        assert len(plan.walls) >= 6
        assert len(plan.rooms) >= 3
        assert {r.name for r in plan.rooms} >= {"BED 1", "BED 2", "LIVING"}

    def test_the_reported_counts_match_the_model(self, synthetic_plan: bytes) -> None:
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        report = plan.analysis
        assert report is not None
        assert report.wall_count == len(plan.walls)
        assert report.room_count == len(plan.rooms)
        assert report.label_count == len(plan.labels)
        assert report.dimension_count == len(plan.dimensions)
        assert [s.name for s in report.steps] == [
            "Image processed",
            "Floor plan region detected",
            "Walls detected",
            "Openings detected",
            "Rooms detected",
            "Text detected",
            "Dimensions detected",
            "Scale estimated",
        ]

    def test_the_page_border_is_cropped_away(self, synthetic_plan: bytes) -> None:
        result = analyse(synthetic_plan, "/api/floorplan/images/test.png")
        assert result.region.x > 0 and result.region.y > 0
        assert result.region.width < 1400

    def test_the_disclaimer_does_not_become_a_label(self, synthetic_plan: bytes) -> None:
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        assert not any("DISCLAIMER" in label.text.upper() for label in plan.labels)

    def test_the_scale_is_taken_from_the_dimension_string(self, synthetic_plan: bytes) -> None:
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        assert plan.source is not None
        assert plan.source.scale_method == "ocr-dimensions"
        # The living room is 1000 x 300 px of interior labelled 5.0 x 3.0 m, so
        # a pixel is worth roughly five millimetres.
        assert plan.source.mm_per_px == pytest.approx(5.0, rel=0.25)

    def test_an_explicit_scale_overrides_estimation(self, synthetic_plan: bytes) -> None:
        plan = analyse(
            synthetic_plan, "/api/floorplan/images/test.png", PipelineConfig(mm_per_px_override=4.0)
        ).plan
        assert plan.source is not None
        assert plan.source.mm_per_px == 4.0
        assert plan.source.scale_method == "manual"
        assert plan.source.scale_confidence == 1.0

    def test_walls_are_axis_aligned_and_plausibly_thick(self, synthetic_plan: bytes) -> None:
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        for w in plan.walls:
            assert abs(w.start.x - w.end.x) < 1.0 or abs(w.start.y - w.end.y) < 1.0
            assert 20.0 < w.thickness < 600.0

    def test_every_detected_object_carries_a_confidence_and_a_source(
        self, synthetic_plan: bytes
    ) -> None:
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        for item in [*plan.walls, *plan.rooms, *plan.labels]:
            assert item.confidence is not None
            assert 0.0 <= item.confidence <= 1.0
            assert item.source in {"vision", "ocr", "geometry"}

    def test_room_labels_point_at_the_room_they_name(self, synthetic_plan: bytes) -> None:
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        named = [label for label in plan.labels if label.type == "room" and label.room_id]
        assert named
        room_ids = {r.id for r in plan.rooms}
        assert all(label.room_id in room_ids for label in named)

    def test_doors_and_windows_are_attached_to_real_walls(self, synthetic_plan: bytes) -> None:
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        wall_ids = {w.id for w in plan.walls}
        for opening in [*plan.doors, *plan.windows]:
            assert opening.wall_id in wall_ids
            assert opening.width > 0
            wall = next(w for w in plan.walls if w.id == opening.wall_id)
            length = ((wall.end.x - wall.start.x) ** 2 + (wall.end.y - wall.start.y) ** 2) ** 0.5
            assert 0 <= opening.position <= length

    def test_a_doorway_does_not_split_its_wall_in_two(self, synthetic_plan: bytes) -> None:
        # The corridor is drawn as three runs with a doorway between each. Once
        # the openings are resolved it should be one wall, not three.
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        corridor = [
            w
            for w in plan.walls
            if abs(w.start.y - w.end.y) < 1.0
            and abs(w.end.x - w.start.x) > 0.8 * max(
                abs(x.end.x - x.start.x) for x in plan.walls
            )
        ]
        assert corridor, "the corridor wall should have been healed into one run"

    def test_a_blank_page_is_rejected_with_an_explanation(self) -> None:
        import cv2
        import numpy as np

        blank = np.full((800, 800, 3), 255, np.uint8)
        _, buffer = cv2.imencode(".png", blank)
        with pytest.raises(AnalysisError, match="No walls"):
            analyse(bytes(buffer), "/api/floorplan/images/blank.png")

    def test_a_non_image_upload_is_rejected(self) -> None:
        with pytest.raises(ImageLoadError):
            load_image(b"this is not an image")

    def test_a_tiny_image_is_rejected(self) -> None:
        import cv2
        import numpy as np

        _, buffer = cv2.imencode(".png", np.zeros((50, 50, 3), np.uint8))
        with pytest.raises(ImageLoadError, match="200 px"):
            load_image(bytes(buffer))


class TestFloorPlanSerialisation:
    def test_the_wire_format_is_camel_case(self, synthetic_plan: bytes) -> None:
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        payload = plan.model_dump(by_alias=True, exclude_none=True)

        assert payload["units"] == "mm"
        assert "labelPosition" in payload["rooms"][0]
        assert "mmPerPx" in payload["source"]
        assert "planRegion" in payload["source"]
        assert "scaleConfidence" in payload["source"]
        assert "wallCount" in payload["analysis"]

    def test_a_plan_survives_a_json_round_trip(self, synthetic_plan: bytes) -> None:
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        text = json.dumps(plan.model_dump(by_alias=True, exclude_none=True))
        restored = models.FloorPlan.model_validate(json.loads(text))

        assert restored.walls == plan.walls
        assert restored.rooms == plan.rooms
        assert restored.labels == plan.labels
        assert restored.dimensions == plan.dimensions
        assert restored.source == plan.source

    def test_an_empty_plan_is_valid(self) -> None:
        plan = models.FloorPlan()
        assert plan.model_dump(by_alias=True)["walls"] == []
        assert plan.version == models.FLOORPLAN_VERSION

    def test_snake_case_input_is_accepted_too(self) -> None:
        plan = models.FloorPlan.model_validate(
            {
                "version": 1,
                "units": "mm",
                "rooms": [
                    {
                        "id": "room_001",
                        "name": "LIVING",
                        "polygon": [{"x": 0, "y": 0}, {"x": 1, "y": 0}, {"x": 1, "y": 1}],
                        "label_position": {"x": 0.5, "y": 0.5},
                    }
                ],
            }
        )
        assert plan.rooms[0].label_position.x == 0.5

    def test_malformed_geometry_is_rejected(self) -> None:
        from pydantic import ValidationError

        with pytest.raises(ValidationError):
            models.FloorPlan.model_validate(
                {"walls": [{"id": "w1", "start": {"x": 0}, "end": {"x": 1, "y": 0}, "thickness": 1}]}
            )

    def test_units_other_than_millimetres_are_rejected(self) -> None:
        from pydantic import ValidationError

        with pytest.raises(ValidationError):
            models.FloorPlan.model_validate({"units": "ft"})


class TestSlidingDoors:
    def test_sliding_doors_are_carried_as_doors_with_a_style(
        self, synthetic_plan: bytes
    ) -> None:
        plan = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        for door in plan.doors:
            assert door.style in {"swing", "sliding"}

    def test_a_door_defaults_to_swinging(self) -> None:
        door = models.Door(
            id="door_001", wall_id="wall_001", position=1000, width=870
        )
        assert door.style == "swing"

    def test_the_style_survives_a_round_trip(self) -> None:
        plan = models.FloorPlan(
            doors=[
                models.Door(
                    id="door_001",
                    wall_id="wall_001",
                    position=1000,
                    width=1800,
                    style="sliding",
                )
            ]
        )
        payload = plan.model_dump(by_alias=True, exclude_none=True)
        assert payload["doors"][0]["style"] == "sliding"
        assert models.FloorPlan.model_validate(payload).doors[0].style == "sliding"


class TestTheTextIsReadAlongsideTheGeometry:
    """OCR runs on its own thread while the walls are found.

    They are independent — neither stage sees the other's output — so the only
    things that can go wrong are the ones threading introduces: a failure that
    escapes the wrong way, or a result that depends on timing.
    """

    def test_a_missing_tesseract_does_not_fail_the_analysis(
        self, synthetic_plan: bytes, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """It has to come back as a warning, not an exception off a thread."""

        def unavailable(*_args: object, **_kwargs: object) -> None:
            raise ocr.OcrUnavailable("Tesseract is not installed.")

        monkeypatch.setattr(ocr, "extract_text", unavailable)
        result = analyse(synthetic_plan, "/api/floorplan/images/test.png")

        assert result.plan.walls, "the geometry should survive losing the text"
        assert any("Tesseract" in w for w in result.plan.analysis.warnings)
        assert any(s.name == "Text detected" and not s.ok for s in result.plan.analysis.steps)

    def test_the_same_page_analyses_the_same_way_twice(self, synthetic_plan: bytes) -> None:
        """Readings are merged in a fixed order, not the order they finish."""
        first = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        second = analyse(synthetic_plan, "/api/floorplan/images/test.png").plan
        assert first.model_dump() == second.model_dump()
