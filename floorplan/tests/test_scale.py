"""Scale estimation and the millimetre conversion that depends on it."""

from __future__ import annotations

import pytest

from app.config import DEFAULTS, with_overrides
from app.pipeline.ocr import parse_dimensions
from app.pipeline.scale import (
    MeasuredRoom,
    ScaleSample,
    calibrate,
    estimate_scale,
    median,
    px_to_mm,
    samples_from_rooms,
)


class TestDimensionParsing:
    @pytest.mark.parametrize(
        ("text", "expected"),
        [
            ("2.5m X 3.5m", (2500.0, 3500.0)),
            ("3.5m x 6.0m", (3500.0, 6000.0)),
            ("3.5m×4.0m", (3500.0, 4000.0)),
            ("GARAGE 3.5m X 6.0m", (3500.0, 6000.0)),
            ("2,5m X 3,5m", (2500.0, 3500.0)),  # comma decimal
            ("3500 X 6000", (3500.0, 6000.0)),  # bare millimetres
            ("3.5 x 4.0", (3500.0, 4000.0)),  # bare metres
            ("4000mm x 3000mm", (4000.0, 3000.0)),
        ],
    )
    def test_recognised_forms(self, text: str, expected: tuple[float, float]) -> None:
        assert parse_dimensions(text) == expected

    @pytest.mark.parametrize(
        "text",
        [
            "MASTER BED",
            "",
            "BED 3",
            "500m X 900m",  # half a kilometre is not a room
            "0 x 5",
            "0.1m x 0.2m",  # nor is a shoebox
        ],
    )
    def test_rejected_forms(self, text: str) -> None:
        assert parse_dimensions(text) is None


class TestSamplesFromRooms:
    def test_pixel_and_millimetre_pairs_are_matched_by_size(self) -> None:
        # The label does not say which value is the width, so the long side of
        # the room must go with the long dimension.
        room = MeasuredRoom(
            width_px=600.0, height_px=350.0, dimensions_mm=(3500.0, 6000.0), name="GARAGE"
        )
        samples = samples_from_rooms([room], aspect_tolerance=0.3)
        assert [round(s.mm_per_px, 4) for s in samples] == [10.0, 10.0]

    def test_a_degenerate_room_contributes_nothing(self) -> None:
        # No width means no shape to check the label against, so nothing here
        # can be trusted to set the scale of the whole plan.
        room = MeasuredRoom(width_px=0.0, height_px=350.0, dimensions_mm=(3500.0, 6000.0))
        assert samples_from_rooms([room], aspect_tolerance=0.3) == []


class TestEstimateScale:
    def test_agreeing_samples_give_a_confident_estimate(self) -> None:
        samples = [ScaleSample(10.0, ""), ScaleSample(10.1, ""), ScaleSample(9.9, "")]
        estimate = estimate_scale(samples, DEFAULTS, plan_width_px=1000)
        assert estimate.mm_per_px == pytest.approx(10.0)
        assert estimate.method == "ocr-dimensions"
        assert estimate.confidence > 0.9

    def test_one_wild_sample_is_voted_out(self) -> None:
        samples = [ScaleSample(10.0, ""), ScaleSample(10.2, ""), ScaleSample(97.0, "")]
        estimate = estimate_scale(samples, DEFAULTS, plan_width_px=1000)
        # The two that agree with each other win; the outlier is dropped, not
        # averaged in.
        assert estimate.mm_per_px == pytest.approx(10.1)
        assert estimate.confidence < 0.9
        assert "disagreed" in estimate.note

    def test_a_scale_implying_an_impossible_wall_is_refused(self) -> None:
        # 10 px walls at 45 mm/px would be 450 mm of masonry throughout.
        estimate = estimate_scale(
            [ScaleSample(45.0, ""), ScaleSample(46.0, "")],
            DEFAULTS,
            plan_width_px=1000,
            wall_thickness_px=10.0,
        )
        assert estimate.method == "wall-thickness"
        assert estimate.mm_per_px == pytest.approx(11.0)
        assert "impossible" in estimate.note

    def test_a_plausible_scale_passes_the_wall_check(self) -> None:
        estimate = estimate_scale(
            [ScaleSample(10.0, ""), ScaleSample(10.1, "")],
            DEFAULTS,
            plan_width_px=1000,
            wall_thickness_px=11.0,
        )
        assert estimate.method == "ocr-dimensions"

    def test_a_room_whose_shape_contradicts_its_label_is_not_measured(self) -> None:
        # "3.6 x 4.0" is nearly square; a region twice as long as it is wide is
        # not that room, it is the open-plan area that swallowed it.
        wrong = MeasuredRoom(
            width_px=200.0, height_px=800.0, dimensions_mm=(3600.0, 4000.0), name="BED"
        )
        assert samples_from_rooms([wrong], aspect_tolerance=0.3) == []

    def test_a_single_sample_is_never_treated_as_certain(self) -> None:
        estimate = estimate_scale([ScaleSample(10.0, "")], DEFAULTS, plan_width_px=1000)
        assert estimate.confidence <= 0.6

    def test_no_samples_falls_back_to_an_assumed_plan_width(self) -> None:
        settings = with_overrides(fallback_plan_width_mm=15000.0)
        estimate = estimate_scale([], settings, plan_width_px=1500)
        assert estimate.method == "fallback"
        assert estimate.mm_per_px == pytest.approx(10.0)
        assert estimate.confidence < 0.3
        assert "calibrate" in estimate.note.lower()

    def test_median_of_an_even_count_averages_the_middle_pair(self) -> None:
        assert median([1.0, 2.0, 3.0, 4.0]) == pytest.approx(2.5)
        assert median([3.0, 1.0, 2.0]) == pytest.approx(2.0)
        with pytest.raises(ValueError):
            median([])


class TestCalibration:
    def test_a_known_length_gives_the_scale(self) -> None:
        assert calibrate(pixels=342.0, millimetres=3500.0) == pytest.approx(10.2339, rel=1e-4)

    def test_calibration_is_the_inverse_of_conversion(self) -> None:
        mm_per_px = calibrate(pixels=400.0, millimetres=3600.0)
        assert px_to_mm((400.0, 0.0), mm_per_px)[0] == pytest.approx(3600.0)

    @pytest.mark.parametrize(("pixels", "millimetres"), [(0, 100), (-1, 100), (100, 0), (100, -5)])
    def test_nonsense_inputs_are_rejected(self, pixels: float, millimetres: float) -> None:
        with pytest.raises(ValueError):
            calibrate(pixels=pixels, millimetres=millimetres)


class TestCoordinateConversion:
    def test_pixels_convert_to_millimetres(self) -> None:
        assert px_to_mm((100.0, 250.0), 8.8) == pytest.approx((880.0, 2200.0))

    def test_the_origin_is_the_top_left_of_the_plan_region(self) -> None:
        assert px_to_mm((0.0, 0.0), 12.5) == (0.0, 0.0)
