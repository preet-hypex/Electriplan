"""Room polygon generation from a reconstructed wall set."""

from __future__ import annotations

import pytest

from app.config import DEFAULTS, derive, with_overrides
from app.pipeline.geometry import Segment
from app.pipeline.rooms import bridge_openings, detect_rooms, rectilinear_simplify


def wall(x1: float, y1: float, x2: float, y2: float, thickness: float = 10.0) -> Segment:
    return Segment(x1=x1, y1=y1, x2=x2, y2=y2, thickness=thickness)


def canvas(width: int = 800, height: int = 600, thickness: float = 10.0):
    return derive(DEFAULTS, width, height, thickness)


def box(x1: float, y1: float, x2: float, y2: float, thickness: float = 10.0) -> list[Segment]:
    return [
        wall(x1, y1, x2, y1, thickness),
        wall(x2, y1, x2, y2, thickness),
        wall(x2, y2, x1, y2, thickness),
        wall(x1, y2, x1, y1, thickness),
    ]


def area_of(polygon: list[tuple[float, float]]) -> float:
    total = 0.0
    for i in range(len(polygon)):
        a, b = polygon[i], polygon[(i + 1) % len(polygon)]
        total += a[0] * b[1] - b[0] * a[1]
    return abs(total) / 2.0


class TestRoomDetection:
    def test_a_closed_rectangle_is_one_room(self) -> None:
        detection = detect_rooms(box(100, 100, 500, 400), DEFAULTS, canvas())
        assert len(detection.rooms) == 1
        room = detection.rooms[0]
        assert len(room.polygon) == 4
        # The polygon follows the inner faces, so it is a wall-thickness smaller
        # than the centre lines in each direction.
        assert area_of(room.polygon) == pytest.approx(390 * 290, rel=0.06)

    def test_a_divided_rectangle_is_two_rooms(self) -> None:
        segments = box(100, 100, 500, 400) + [wall(300, 100, 300, 400)]
        detection = detect_rooms(segments, DEFAULTS, canvas())
        assert len(detection.rooms) == 2

    def test_a_doorway_in_the_divider_still_gives_two_rooms(self) -> None:
        # The gap is bridged for detection but no wall is invented in the model.
        segments = box(100, 100, 500, 400) + [
            wall(300, 100, 300, 210),
            wall(300, 300, 300, 400),
        ]
        detection = detect_rooms(segments, DEFAULTS, canvas())
        assert len(detection.rooms) == 2
        assert detection.virtual_segments, "the doorway should have been bridged"

    def test_a_wide_hole_is_not_bridged_and_the_rooms_stay_joined(self) -> None:
        segments = box(100, 100, 500, 400) + [
            wall(300, 100, 300, 130),
            wall(300, 380, 300, 400),
        ]
        detection = detect_rooms(segments, DEFAULTS, canvas())
        assert len(detection.rooms) == 1

    def test_an_open_rectangle_encloses_nothing(self) -> None:
        segments = box(100, 100, 500, 400)[:3]  # three sides
        assert detect_rooms(segments, DEFAULTS, canvas()).rooms == []

    def test_no_walls_means_no_rooms(self) -> None:
        assert detect_rooms([], DEFAULTS, canvas()).rooms == []

    def test_a_region_smaller_than_the_threshold_is_rejected(self) -> None:
        settings = with_overrides(min_room_area_frac=0.5)
        detection = detect_rooms(box(100, 100, 500, 400), settings, canvas())
        assert detection.rooms == []
        assert detection.rejected == 1

    def test_rooms_come_back_largest_first(self) -> None:
        segments = box(100, 100, 700, 500) + [wall(250, 100, 250, 500)]
        rooms = detect_rooms(segments, DEFAULTS, canvas()).rooms
        assert rooms[0].area > rooms[1].area

    def test_the_label_point_lands_inside_an_l_shaped_room(self) -> None:
        segments = [
            wall(100, 100, 600, 100),
            wall(600, 100, 600, 200),
            wall(600, 200, 220, 200),
            wall(220, 200, 220, 500),
            wall(220, 500, 100, 500),
            wall(100, 500, 100, 100),
        ]
        rooms = detect_rooms(segments, DEFAULTS, canvas()).rooms
        assert len(rooms) == 1
        x, y = rooms[0].label_point
        in_arm = 100 < x < 600 and 100 < y < 200
        in_leg = 100 < x < 220 and 100 < y < 500
        assert in_arm or in_leg

    def test_a_room_closed_only_by_bridges_scores_lower_than_a_walled_one(self) -> None:
        walled = detect_rooms(box(100, 100, 400, 400), DEFAULTS, canvas()).rooms[0]
        gappy = detect_rooms(
            [
                wall(100, 100, 250, 100),
                wall(330, 100, 400, 100),
                wall(400, 100, 400, 400),
                wall(400, 400, 100, 400),
                wall(100, 400, 100, 100),
            ],
            DEFAULTS,
            canvas(),
        ).rooms[0]
        assert gappy.confidence < walled.confidence


class TestBridging:
    def test_a_doorway_in_one_wall_line_is_bridged(self) -> None:
        segments = [wall(0, 100, 200, 100), wall(280, 100, 500, 100)]
        bridges = bridge_openings(segments, DEFAULTS, canvas())
        assert len(bridges) == 1
        assert bridges[0].length == pytest.approx(80.0)

    def test_two_ends_that_meet_at_right_angles_are_bridged(self) -> None:
        # The entry-to-porch case: the boundary steps around a corner.
        segments = [wall(0, 100, 300, 100), wall(280, 190, 500, 190)]
        bridges = bridge_openings(segments, DEFAULTS, canvas())
        assert len(bridges) == 1

    def test_nothing_is_bridged_across_a_diagonal(self) -> None:
        segments = [wall(0, 100, 200, 100), wall(280, 180, 500, 180)]
        assert bridge_openings(segments, DEFAULTS, canvas()) == []

    def test_the_inside_of_a_corner_is_not_bridged(self) -> None:
        # Two walls meeting at a corner already share a node; there is no hole.
        segments = [wall(100, 100, 400, 100), wall(400, 100, 400, 400)]
        assert bridge_openings(segments, DEFAULTS, canvas()) == []

    def test_a_gap_wider_than_a_door_needs_both_ends_loose(self) -> None:
        # 150 px is more than a door (110 px here) but within a garage-door
        # opening, and both ends are loose, so it is bridged.
        loose = [wall(0, 100, 200, 100), wall(350, 100, 700, 100)]
        assert len(bridge_openings(loose, DEFAULTS, canvas())) == 1

        # Attach something to one end and it is no longer a loose end, so only
        # a door-sized gap would qualify — and this one is not.
        attached = loose + [wall(200, 100, 200, 300)]
        assert bridge_openings(attached, DEFAULTS, canvas()) == []

    def test_the_opening_limit_is_capped_relative_to_the_plan(self) -> None:
        # A bad thickness estimate must not let a bridge span the drawing.
        far_apart = [wall(0, 100, 150, 100), wall(600, 100, 780, 100)]
        assert bridge_openings(far_apart, DEFAULTS, canvas()) == []

    def test_bridges_never_enter_the_wall_model(self) -> None:
        segments = [wall(0, 100, 200, 100), wall(280, 100, 500, 100)]
        detection = detect_rooms(box(0, 0, 700, 500) + segments, DEFAULTS, canvas())
        assert all(s.confidence == 0.0 for s in detection.virtual_segments)


class TestRectilinearSimplify:
    def test_a_traced_staircase_becomes_a_clean_rectangle(self) -> None:
        traced = [(0.0, 0.0), (100.0, 1.0), (101.0, 60.0), (99.0, 61.0), (1.0, 59.0)]
        simplified = rectilinear_simplify(traced, tolerance=5.0)
        assert len(simplified) == 4
        xs = sorted({round(p[0]) for p in simplified})
        ys = sorted({round(p[1]) for p in simplified})
        assert len(xs) == 2 and len(ys) == 2

    def test_vertices_that_sit_on_a_straight_edge_are_dropped(self) -> None:
        with_extra = [(0.0, 0.0), (50.0, 0.0), (100.0, 0.0), (100.0, 60.0), (0.0, 60.0)]
        assert len(rectilinear_simplify(with_extra, tolerance=4.0)) == 4

    def test_a_real_corner_is_kept(self) -> None:
        l_shape = [
            (0.0, 0.0),
            (200.0, 0.0),
            (200.0, 60.0),
            (60.0, 60.0),
            (60.0, 200.0),
            (0.0, 200.0),
        ]
        assert len(rectilinear_simplify(l_shape, tolerance=4.0)) == 6

    def test_degenerate_input_is_returned_unchanged(self) -> None:
        assert rectilinear_simplify([(0.0, 0.0), (1.0, 1.0)], tolerance=4.0) == [
            (0.0, 0.0),
            (1.0, 1.0),
        ]
