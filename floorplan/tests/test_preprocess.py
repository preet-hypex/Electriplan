"""Measuring the drawing: how thick its walls are, and how thick most of them are."""

from __future__ import annotations

import cv2
import numpy as np
import pytest

from app.config import DEFAULTS, derive
from app.pipeline.preprocess import (
    binarise,
    dominant_stroke_width,
    estimate_wall_thickness,
)


def two_weight_plan(outer: int = 12, inner: int = 4) -> np.ndarray:
    """A house drawn the way plans often are: heavy outside, lighter within.

    Plus hairline fittings, because a plan always has some and there is usually
    more of them than there are walls. They are spaced well apart on purpose —
    a tight hatch of hairlines is collectively as solid as a wall and survives
    any opening, which is a real effect but not the one under test.
    """
    page = np.full((700, 700), 255, np.uint8)
    cv2.rectangle(page, (60, 60), (640, 640), 0, outer)
    for i in range(3):
        step = 60 + (i + 1) * 145
        cv2.line(page, (step, 60), (step, 640), 0, inner)
        cv2.line(page, (60, step), (640, step), 0, inner)
    for i in range(30):
        cv2.line(page, (90, 80 + i * 9), (600, 80 + i * 9), 0, 1)
    return page


def single_weight_plan(weight: int = 8) -> np.ndarray:
    page = np.full((700, 700), 255, np.uint8)
    cv2.rectangle(page, (60, 60), (640, 640), 0, weight)
    cv2.line(page, (350, 60), (350, 640), 0, weight)
    cv2.line(page, (60, 350), (640, 350), 0, weight)
    return page


class TestTwoWallWeights:
    """The commonest wall, not the thickest, is what the opening may erase.

    Wall thickness is a high percentile, which answers "how thick are the
    thickest walls". That is the right question for the tolerances that scale
    off it and the wrong one for sizing the opening: on a plan drawn heavy
    outside and light within, the percentile lands on the heavy walls and the
    opening is then wider than the partitions it is supposed to keep.
    """

    def test_the_thickness_estimate_follows_the_heavy_walls(self) -> None:
        ink = binarise(two_weight_plan())
        assert estimate_wall_thickness(ink, DEFAULTS, 40) >= 10.0

    def test_the_stroke_mode_comes_out_below_it(self) -> None:
        """The number the opening kernel has to respect."""
        ink = binarise(two_weight_plan())
        thickness = estimate_wall_thickness(ink, DEFAULTS, 40)
        assert dominant_stroke_width(ink, DEFAULTS, 40) < thickness

    def test_hairline_fittings_do_not_become_the_mode(self) -> None:
        """The mode is a vote, and hairlines outnumber walls four to one here.

        Hatching, leader lines and furniture outlines are drawn thinner than
        any wall and there are far more of them, so without a floor they win
        the vote outright and the opening kernel collapses to nothing.
        """
        ink = binarise(two_weight_plan())
        assert dominant_stroke_width(ink, DEFAULTS, 40) > DEFAULTS.hairline_max_px

    def test_the_kernel_shrinks_but_nothing_else_moves(self) -> None:
        plain = derive(DEFAULTS, 700, 700, 12.0)
        split = derive(DEFAULTS, 700, 700, 12.0, 4.0)
        assert split.open_kernel_px < plain.open_kernel_px
        # Every tolerance in the pipeline scales off the thickness and the
        # minimum length; neither may move, or this stops being a fix to one
        # stage and becomes a change to all of them.
        assert split.wall_thickness_px == plain.wall_thickness_px
        assert split.min_wall_length_px == plain.min_wall_length_px

    def test_the_kernel_never_grows(self) -> None:
        """A mode above the thickness must not widen the opening."""
        assert (
            derive(DEFAULTS, 700, 700, 8.0, 20.0).open_kernel_px
            == derive(DEFAULTS, 700, 700, 8.0).open_kernel_px
        )

    def test_a_plan_drawn_at_one_weight_is_left_alone(self) -> None:
        """The point of the whole change: it does nothing when there is one weight."""
        ink = binarise(single_weight_plan())
        thickness = estimate_wall_thickness(ink, DEFAULTS, 40)
        mode = dominant_stroke_width(ink, DEFAULTS, 40)
        assert derive(DEFAULTS, 700, 700, thickness, mode).open_kernel_px == (
            derive(DEFAULTS, 700, 700, thickness).open_kernel_px
        )

    def test_a_blank_page_does_not_crash(self) -> None:
        blank = np.zeros((200, 200), np.uint8)
        assert dominant_stroke_width(blank, DEFAULTS, 40) == pytest.approx(
            DEFAULTS.min_wall_thickness_px
        )
