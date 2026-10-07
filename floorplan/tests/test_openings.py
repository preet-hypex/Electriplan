"""Doors, windows, and healing the walls they interrupt."""

from __future__ import annotations

import cv2
import numpy as np
import pytest

from app.config import DEFAULTS, derive
from app.pipeline.geometry import Segment
from app.pipeline.openings import (
    _dash_count,
    classify_gap,
    find_gaps,
    resolve_openings,
)


def wall(x1: float, y1: float, x2: float, y2: float, thickness: float = 10.0) -> Segment:
    return Segment(x1=x1, y1=y1, x2=x2, y2=y2, thickness=thickness)


def canvas(width: int = 800, height: int = 600, thickness: float = 10.0):
    return derive(DEFAULTS, width, height, thickness)


def blank(width: int = 800, height: int = 600) -> np.ndarray:
    return np.zeros((height, width), np.uint8)


def draw_wall_pair(mask: np.ndarray, y: int, gap: tuple[int, int], thickness: int = 10) -> None:
    cv2.line(mask, (0, y), (gap[0], y), 255, thickness)
    cv2.line(mask, (gap[1], y), (mask.shape[1] - 1, y), 255, thickness)


class TestFindingGaps:
    def test_a_doorway_in_one_wall_line_is_found(self) -> None:
        gaps = find_gaps([wall(0, 100, 200, 100), wall(280, 100, 500, 100)], DEFAULTS, canvas())
        assert len(gaps) == 1
        assert gaps[0].collinear
        assert gaps[0].width == pytest.approx(80.0)

    def test_a_corner_step_is_found_but_not_collinear(self) -> None:
        gaps = find_gaps([wall(0, 100, 300, 100), wall(280, 190, 500, 190)], DEFAULTS, canvas())
        assert len(gaps) == 1
        assert not gaps[0].collinear

    def test_the_inside_of_a_corner_is_not_a_gap(self) -> None:
        assert find_gaps([wall(100, 100, 400, 100), wall(400, 100, 400, 400)], DEFAULTS, canvas()) == []

    def test_a_diagonal_span_is_not_a_gap(self) -> None:
        assert find_gaps([wall(0, 100, 200, 100), wall(280, 180, 500, 180)], DEFAULTS, canvas()) == []

    def test_a_gap_wider_than_a_door_needs_both_ends_loose(self) -> None:
        loose = [wall(0, 100, 200, 100), wall(350, 100, 700, 100)]
        assert len(find_gaps(loose, DEFAULTS, canvas())) == 1

        attached = loose + [wall(200, 100, 200, 300)]
        assert find_gaps(attached, DEFAULTS, canvas()) == []

    def test_a_wide_gap_is_accepted_when_a_line_still_spans_it(self) -> None:
        """A window is far wider than a door, but the wall line goes on.

        The corner wall makes the left end non-loose, so the door-width limit
        applies and a 190 px gap is normally refused. A line spanning the gap
        lifts that limit — the wall never really stopped.
        """
        board = canvas(1200, 900)
        segments = [wall(0, 100, 150, 100), wall(150, 100, 150, 300), wall(340, 100, 900, 100)]
        assert find_gaps(segments, DEFAULTS, board) == []

        marks = blank(1200, 900)
        cv2.line(marks, (0, 100), (900, 100), 255, 2)
        gaps = find_gaps(segments, DEFAULTS, board, marks)
        assert len(gaps) == 1
        assert gaps[0].width == pytest.approx(190.0)

    def test_the_absolute_ceiling_still_applies_to_a_spanned_gap(self) -> None:
        """A line across the gap lifts the door limit, not the sanity backstop."""
        board = canvas(800, 600)  # ceiling is 180 px here
        segments = [wall(0, 100, 150, 100), wall(150, 100, 150, 300), wall(500, 100, 780, 100)]
        marks = blank()
        cv2.line(marks, (0, 100), (780, 100), 255, 2)
        assert find_gaps(segments, DEFAULTS, board, marks) == []


class TestGapsTooNarrowToBeOpenings:
    """A crack between two fragments of one wall is not a doorway.

    It matters more than it sounds. Each wall end may be used by one gap, so a
    few-pixel sliver takes an end that a real window or doorway needed, and the
    real opening is then never formed at all — the sliver does not merely add a
    wrong answer, it deletes a right one.
    """

    def test_a_sliver_between_two_fragments_is_not_a_gap(self) -> None:
        # 4 px apart on a 10 px wall: narrower than the wall is thick.
        gaps = find_gaps(
            [wall(0, 100, 200, 100), wall(204, 100, 500, 100)], DEFAULTS, canvas()
        )
        assert gaps == []

class TestClassification:
    def gap(self, width: int = 80):
        (found,) = find_gaps(
            [wall(0, 100, 200, 100), wall(200 + width, 100, 500, 100)], DEFAULTS, canvas()
        )
        return found

    def door_symbol(self, hinge_x: int = 200, width: int = 80, side: int = 1) -> np.ndarray:
        """The whole symbol: leaf at a right angle, then the quarter arc back.

        ``hinge_x`` is one of the two jambs (200 or 280); the arc always closes
        onto the other one, so the sweep depends on which jamb is hinged.
        ``side`` is +1 for a leaf drawn below the wall (y increasing), -1 above.
        """
        ink = blank()
        far_x = 280 if hinge_x == 200 else 200
        cv2.line(ink, (hinge_x, 100), (hinge_x, 100 + width * side), 255, 2)

        leaf_deg = 90 if side > 0 else 270
        towards_deg = 0 if far_x > hinge_x else 180
        low, high = sorted((leaf_deg, towards_deg))
        if high - low > 180:  # go the short way round
            low, high = high, low + 360
        cv2.ellipse(ink, (hinge_x, 100), (width, width), 0, low, high, 255, 2)
        return ink

    def test_an_empty_gap_is_a_plain_opening(self) -> None:
        kind, _, swing = classify_gap(self.gap(), blank(), DEFAULTS, canvas(), blank())
        assert kind == "opening"
        assert swing is None

    def test_a_line_across_the_gap_is_a_window(self) -> None:
        marks = blank()
        cv2.line(marks, (200, 100), (280, 100), 255, 2)
        kind, confidence, _ = classify_gap(self.gap(), blank(), DEFAULTS, canvas(), marks)
        assert kind == "window"
        assert 0.4 <= confidence <= 0.85

    def test_the_whole_door_symbol_is_a_door(self) -> None:
        kind, confidence, swing = classify_gap(
            self.gap(), self.door_symbol(), DEFAULTS, canvas(), blank()
        )
        assert kind == "door"
        assert confidence >= 0.4
        assert swing is not None
        assert swing.leaf >= DEFAULTS.door_decisive_leaf
        assert swing.arc >= DEFAULTS.door_min_arc

    def test_an_arc_with_no_leaf_is_still_a_door(self) -> None:
        """The leaf is the half that lands under a fitting; the arc carries it."""
        ink = blank()
        cv2.ellipse(ink, (200, 100), (80, 80), 0, 0, 90, 255, 2)
        kind, _, _ = classify_gap(self.gap(), ink, DEFAULTS, canvas(), blank())
        assert kind == "door"

    def test_a_line_across_the_gap_beats_a_bare_arc(self) -> None:
        """Basins and baths are curved and sit against the walls windows are in.

        With no leaf to settle it, a line spanning the gap is the better
        evidence, so the window wins and a curved fitting cannot invent a door.
        """
        ink = blank()
        cv2.ellipse(ink, (200, 100), (80, 80), 0, 0, 90, 255, 2)
        marks = blank()
        cv2.line(marks, (200, 100), (280, 100), 255, 2)
        kind, _, _ = classify_gap(self.gap(), ink, DEFAULTS, canvas(), marks)
        assert kind == "window"

        # ...but a clear leaf settles it the other way, spanning line or not.
        kind, _, _ = classify_gap(self.gap(), self.door_symbol(), DEFAULTS, canvas(), marks)
        assert kind == "door"

    def sliding_symbol(self, width: int = 80, gap_start: int = 200) -> np.ndarray:
        """Two panels passing one another, offset to either side of the wall.

        Each covers most of the opening; together they span it, and they
        overlap in the middle where they pass.
        """
        marks = blank()
        reach = int(width * 0.62)
        cv2.line(marks, (gap_start, 96), (gap_start + reach, 96), 255, 2)
        cv2.line(marks, (gap_start + width - reach, 104), (gap_start + width, 104), 255, 2)
        return marks

    def test_two_offset_panels_are_a_sliding_door(self) -> None:
        kind, confidence, swing = classify_gap(
            self.gap(), blank(), DEFAULTS, canvas(), self.sliding_symbol()
        )
        assert kind == "sliding"
        # Held low on purpose: this is a suggestion to check, not a finding.
        assert confidence <= 0.6
        assert swing is None

    def test_one_line_across_the_gap_is_still_a_window(self) -> None:
        """The difference is whether it takes one line or two."""
        marks = blank()
        cv2.line(marks, (200, 100), (280, 100), 255, 2)
        kind, _, _ = classify_gap(self.gap(), blank(), DEFAULTS, canvas(), marks)
        assert kind == "window"

    def test_two_panels_on_the_same_side_are_not_a_sliding_door(self) -> None:
        """Panels have to be offset across the wall; that is what passing means."""
        marks = blank()
        cv2.line(marks, (200, 100), (250, 100), 255, 2)
        cv2.line(marks, (230, 100), (280, 100), 255, 2)
        kind, _, _ = classify_gap(self.gap(), blank(), DEFAULTS, canvas(), marks)
        assert kind != "sliding"

    def test_two_panels_that_never_meet_are_not_a_sliding_door(self) -> None:
        """A gap between them means they do not close the opening."""
        marks = blank()
        cv2.line(marks, (200, 96), (225, 96), 255, 2)
        cv2.line(marks, (255, 104), (280, 104), 255, 2)
        kind, _, _ = classify_gap(self.gap(), blank(), DEFAULTS, canvas(), marks)
        assert kind != "sliding"

    def test_a_door_symbol_still_wins_over_a_sliding_reading(self) -> None:
        ink = self.door_symbol()
        kind, _, _ = classify_gap(
            self.gap(), ink, DEFAULTS, canvas(), self.sliding_symbol()
        )
        assert kind == "door"

    def wide_gap(self, width: int = 160):
        (found,) = find_gaps(
            [wall(0, 100, 200, 100), wall(200 + width, 100, 500, 100)], DEFAULTS, canvas()
        )
        return found

    def garage_symbol(
        self, gap_start: int = 200, width: int = 160, period: int = 10
    ) -> np.ndarray:
        """A dashed rectangle spanning the opening: the garage door symbol.

        Two long sides either side of the wall's centre line, each broken at a
        regular period. The break is the whole signal.
        """
        marks = blank()
        for side in (-4, 4):
            for x in range(gap_start, gap_start + width, period):
                cv2.line(marks, (x, 100 + side), (x + period // 2, 100 + side), 255, 2)
        return marks

    def test_a_dashed_line_across_the_gap_is_a_garage_door(self) -> None:
        kind, confidence, swing = classify_gap(
            self.wide_gap(), blank(), DEFAULTS, canvas(), self.garage_symbol()
        )
        assert kind == "garage"
        assert confidence >= 0.4
        assert swing is None

    def test_an_unbroken_line_across_the_same_gap_is_a_window(self) -> None:
        """What separates them is only whether the line is broken."""
        marks = blank()
        cv2.line(marks, (200, 100), (360, 100), 255, 2)
        kind, _, _ = classify_gap(self.wide_gap(), blank(), DEFAULTS, canvas(), marks)
        assert kind == "window"

    def test_a_line_with_a_few_breaks_is_not_a_garage_door(self) -> None:
        kind, _, _ = classify_gap(
            self.wide_gap(), blank(), DEFAULTS, canvas(), self.garage_symbol(period=40)
        )
        assert kind != "garage"

    def test_marks_too_spread_out_for_the_wall_are_not_a_garage_door(self) -> None:
        """Enough separate marks to be counted, but too far apart to be dashes.

        Dash period is measured against the wall's own thickness, which is what
        makes the reading survive a change of resolution. On a thin-walled plan
        the very same marks are a scattering rather than a dashed line, and
        this is the guard that says so — the dash count alone cannot, because
        the marks are there either way.
        """
        thin_walls = [wall(0, 100, 200, 100, 4.0), wall(360, 100, 500, 100, 4.0)]
        thin = canvas(thickness=4.0)
        (gap,) = find_gaps(thin_walls, DEFAULTS, thin, self.garage_symbol(period=4))

        spread = self.garage_symbol(period=13)
        assert _dash_count(gap.p1, gap.p2, spread, DEFAULTS, thin)[0] >= DEFAULTS.garage_min_dashes
        kind, _, _ = classify_gap(gap, blank(), DEFAULTS, thin, spread)
        assert kind != "garage"

        # The same marks at a period this wall's thickness justifies do read.
        kind, _, _ = classify_gap(gap, blank(), DEFAULTS, thin, self.garage_symbol(period=4))
        assert kind == "garage"

    def test_a_dashed_line_lifts_the_width_guard(self) -> None:
        """A garage opening is wider than any door, so it needs the evidence.

        Without it the gap is thrown out before anything gets to look at it,
        which is how these were being lost.
        """
        thin_walls = [wall(0, 100, 200, 100, 4.0), wall(360, 100, 500, 100, 4.0)]
        thin = canvas(thickness=4.0)
        assert find_gaps(thin_walls, DEFAULTS, thin) == []

        (gap,) = find_gaps(thin_walls, DEFAULTS, thin, self.garage_symbol(period=4))
        assert gap.width == pytest.approx(160.0)

    def test_dashes_over_part_of_the_gap_are_not_a_garage_door(self) -> None:
        """Marks bunched at one end are some fitting poking in, not a door.

        Deliberately enough dashes, at a believable period, so that the only
        thing wrong with them is that they stop three-quarters of the way
        across. A garage door fills its opening.
        """
        bunched = self.garage_symbol(width=100, period=8)
        gap = self.wide_gap()
        run = _dash_count(gap.p1, gap.p2, bunched, DEFAULTS, canvas())
        kind, _, _ = classify_gap(gap, blank(), DEFAULTS, canvas(), bunched)
        assert kind != "garage", f"dashes={run}"

    def test_a_door_symbol_still_wins_over_a_garage_reading(self) -> None:
        kind, _, _ = classify_gap(
            self.wide_gap(),
            self.door_symbol(width=160),
            DEFAULTS,
            canvas(),
            self.garage_symbol(),
        )
        assert kind == "door"

    def test_a_garage_door_heals_the_wall_and_is_recorded(self) -> None:
        """The point of identifying it: one wall again, with the door on it."""
        ink = blank()
        draw_wall_pair(ink, 100, (200, 360))
        result = resolve_openings(
            [wall(0, 100, 200, 100), wall(360, 100, 500, 100)],
            ink,
            DEFAULTS,
            canvas(),
            self.garage_symbol(),
        )
        assert len(result.walls) == 1
        (garage,) = result.garages
        assert garage.width == pytest.approx(160.0, abs=2.0)

    def test_a_leaf_with_no_arc_is_not_a_door(self) -> None:
        ink = blank()
        cv2.line(ink, (200, 100), (200, 180), 255, 2)
        kind, _, _ = classify_gap(self.gap(), ink, DEFAULTS, canvas(), blank())
        assert kind == "opening"

    def test_the_hinge_and_the_swing_side_are_recovered(self) -> None:
        """Which jamb and which side, read off the drawing rather than assumed."""
        left = classify_gap(self.gap(), self.door_symbol(hinge_x=200), DEFAULTS, canvas(), blank())[2]
        assert left is not None and left.hinge_at_p1

        right = classify_gap(
            self.gap(), self.door_symbol(hinge_x=280), DEFAULTS, canvas(), blank()
        )[2]
        assert right is not None and not right.hinge_at_p1

        below = classify_gap(self.gap(), self.door_symbol(side=1), DEFAULTS, canvas(), blank())[2]
        above = classify_gap(self.gap(), self.door_symbol(side=-1), DEFAULTS, canvas(), blank())[2]
        assert below is not None and above is not None
        assert below.swing_sign != above.swing_sign

    def test_a_leaf_lying_across_the_gap_is_not_read_as_a_window(self) -> None:
        """The window test is shape: a line *parallel* to the wall, not any line."""
        marks = blank()
        cv2.line(marks, (240, 60), (240, 140), 255, 2)  # perpendicular
        kind, _, _ = classify_gap(self.gap(), blank(), DEFAULTS, canvas(), marks)
        assert kind == "opening"


class TestHealingWalls:
    def test_a_door_gap_fuses_its_two_walls_into_one(self) -> None:
        ink = blank()
        cv2.line(ink, (200, 100), (200, 180), 255, 2)
        cv2.ellipse(ink, (200, 100), (80, 80), 0, 0, 90, 255, 2)
        segments = [wall(0, 100, 200, 100), wall(280, 100, 500, 100)]

        result = resolve_openings(segments, ink, DEFAULTS, canvas(), blank())

        assert len(result.walls) == 1
        assert result.walls[0].length == pytest.approx(500.0)
        assert len(result.doors) == 1
        # The door sits where the gap was, measured along the healed wall.
        assert result.doors[0].position == pytest.approx(240.0, abs=2.0)
        assert result.doors[0].width == pytest.approx(80.0)
        # Hinged at the jamb nearer the wall's start, which is where it is drawn.
        assert result.doors[0].hinge_at_start
        assert abs(result.doors[0].swing_degrees) == 90.0

    def test_the_opening_points_at_the_wall_it_belongs_to(self) -> None:
        ink = blank()
        segments = [wall(0, 100, 200, 100), wall(280, 100, 500, 100), wall(0, 400, 500, 400)]
        result = resolve_openings(segments, ink, DEFAULTS, canvas(), blank())
        for opening in result.openings:
            assert 0 <= opening.wall_index < len(result.walls)
            assert 0 <= opening.position <= result.walls[opening.wall_index].length

    def test_a_corner_gap_becomes_a_closure_not_a_wall(self) -> None:
        segments = [wall(0, 100, 300, 100), wall(280, 190, 500, 190)]
        result = resolve_openings(segments, blank(), DEFAULTS, canvas(), blank())
        assert len(result.walls) == 2, "a corner step must not be fused into one wall"
        assert len(result.closures) == 1
        assert result.openings == []

    def test_walls_with_no_gaps_are_returned_unchanged(self) -> None:
        segments = [wall(0, 100, 500, 100), wall(0, 400, 500, 400)]
        result = resolve_openings(segments, blank(), DEFAULTS, canvas(), blank())
        assert len(result.walls) == 2
        assert result.openings == []

    def test_nothing_in_nothing_out(self) -> None:
        result = resolve_openings([], blank(), DEFAULTS, canvas(), blank())
        assert result.walls == [] and result.openings == [] and result.closures == []
