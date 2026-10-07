"""Wall geometry: intersections, merging, snapping, junction closing."""

from __future__ import annotations

import math

import pytest

from app.config import DEFAULTS
from app.pipeline.geometry import (
    Segment,
    align_axes,
    clean,
    drop_slivers,
    collinear_gap,
    deduplicate,
    drop_short,
    extend_to_intersections,
    find_intersections,
    is_collinear,
    line_intersection,
    merge_collinear,
    readmit_stubs,
    segment_intersection,
    snap_endpoints,
    snap_to_axis,
)


def seg(x1: float, y1: float, x2: float, y2: float, thickness: float = 10.0) -> Segment:
    return Segment(x1=x1, y1=y1, x2=x2, y2=y2, thickness=thickness)


class TestWallIntersection:
    def test_perpendicular_walls_cross_at_the_expected_point(self) -> None:
        horizontal = seg(0, 100, 500, 100)
        vertical = seg(200, 0, 200, 400)
        assert segment_intersection(horizontal, vertical) == pytest.approx((200.0, 100.0))

    def test_parallel_walls_never_intersect(self) -> None:
        assert segment_intersection(seg(0, 0, 100, 0), seg(0, 50, 100, 50)) is None
        assert line_intersection(seg(0, 0, 100, 0), seg(0, 50, 100, 50)) is None

    def test_t_junction_counts_as_an_intersection(self) -> None:
        spine = seg(0, 0, 400, 0)
        branch = seg(150, 0, 150, 300)
        assert segment_intersection(spine, branch) == pytest.approx((150.0, 0.0))

    def test_segments_that_only_cross_when_extended_do_not_intersect(self) -> None:
        a = seg(0, 0, 100, 0)
        b = seg(300, -50, 300, 50)
        assert segment_intersection(a, b) is None
        assert line_intersection(a, b) == pytest.approx((300.0, 0.0))

    def test_find_intersections_reports_every_crossing_once(self) -> None:
        # A # shape: two horizontals crossed by two verticals.
        walls = [
            seg(0, 0, 300, 0),
            seg(0, 200, 300, 200),
            seg(100, -50, 100, 250),
            seg(200, -50, 200, 250),
        ]
        assert len(find_intersections(walls)) == 4

    def test_a_degenerate_segment_does_not_blow_up(self) -> None:
        assert segment_intersection(seg(10, 10, 10, 10), seg(0, 0, 100, 100)) is None


class TestCollinearity:
    def test_same_line_disjoint_spans_are_collinear(self) -> None:
        assert is_collinear(seg(0, 0, 100, 0), seg(140, 2, 260, 2), DEFAULTS)

    def test_parallel_but_offset_walls_are_not_collinear(self) -> None:
        assert not is_collinear(seg(0, 0, 100, 0), seg(0, 90, 100, 90), DEFAULTS)

    def test_a_short_leaning_stub_is_still_part_of_its_wall(self) -> None:
        # Three pixels of drift over fifty is nearly four degrees, but it is
        # less than the wall is thick, so it is the same wall.
        long_wall = seg(500, 200, 500, 500, thickness=11)
        stub = seg(503, 60, 500, 110, thickness=11)
        assert is_collinear(long_wall, stub, DEFAULTS)

    def test_perpendicular_walls_are_not_collinear(self) -> None:
        assert not is_collinear(seg(0, 0, 100, 0), seg(50, 0, 50, 100), DEFAULTS)

    def test_gap_is_negative_when_walls_overlap(self) -> None:
        assert collinear_gap(seg(0, 0, 100, 0), seg(140, 0, 200, 0)) == pytest.approx(40)
        assert collinear_gap(seg(0, 0, 100, 0), seg(60, 0, 200, 0)) < 0


class TestWallMerging:
    def test_two_touching_collinear_walls_become_one(self) -> None:
        merged = merge_collinear([seg(0, 0, 100, 0), seg(100, 0, 260, 0)], DEFAULTS)
        assert len(merged) == 1
        assert merged[0].p1 == pytest.approx((0.0, 0.0))
        assert merged[0].p2 == pytest.approx((260.0, 0.0))

    def test_overlapping_walls_merge_to_their_union(self) -> None:
        merged = merge_collinear([seg(0, 0, 180, 0), seg(120, 0, 300, 0)], DEFAULTS)
        assert len(merged) == 1
        assert merged[0].length == pytest.approx(300.0)

    def test_a_doorway_gap_survives_merging(self) -> None:
        # A door is a collinear gap too; merging across it would erase the
        # opening the room detector depends on.
        walls = [seg(0, 0, 300, 0, thickness=10), seg(400, 0, 700, 0, thickness=10)]
        assert len(merge_collinear(walls, DEFAULTS)) == 2

    def test_a_hairline_gap_is_closed(self) -> None:
        walls = [seg(0, 0, 300, 0, thickness=10), seg(308, 0, 700, 0, thickness=10)]
        assert len(merge_collinear(walls, DEFAULTS)) == 1

    def test_perpendicular_walls_are_never_merged(self) -> None:
        assert len(merge_collinear([seg(0, 0, 100, 0), seg(100, 0, 100, 100)], DEFAULTS)) == 2

    def test_merged_thickness_is_length_weighted(self) -> None:
        merged = merge_collinear(
            [seg(0, 0, 100, 0, thickness=10), seg(100, 0, 300, 0, thickness=40)], DEFAULTS
        )
        assert merged[0].thickness == pytest.approx((10 * 100 + 40 * 200) / 300)

    def test_a_chain_of_three_merges_into_one(self) -> None:
        walls = [seg(0, 0, 100, 0), seg(100, 0, 200, 0), seg(200, 0, 300, 0)]
        merged = merge_collinear(walls, DEFAULTS)
        assert len(merged) == 1
        assert merged[0].length == pytest.approx(300.0)

    def test_merging_an_empty_or_single_list_is_a_no_op(self) -> None:
        assert merge_collinear([], DEFAULTS) == []
        one = [seg(0, 0, 10, 0)]
        assert merge_collinear(one, DEFAULTS) == one


class TestCleanupStages:
    def test_short_stubs_are_dropped(self) -> None:
        kept = drop_short([seg(0, 0, 5, 0), seg(0, 0, 100, 0)], min_length=20)
        assert len(kept) == 1

    def test_a_nearly_horizontal_wall_is_squared_up(self) -> None:
        snapped = snap_to_axis([seg(0, 100, 1000, 103)], DEFAULTS)[0]
        assert snapped.y1 == snapped.y2 == pytest.approx(101.5)

    def test_a_short_leaning_stub_is_squared_up_too(self) -> None:
        snapped = snap_to_axis([seg(503, 60, 500, 110, thickness=11)], DEFAULTS)[0]
        assert snapped.x1 == snapped.x2 == pytest.approx(501.5)

    def test_a_genuinely_diagonal_wall_is_left_alone(self) -> None:
        diagonal = seg(0, 0, 300, 300)
        assert snap_to_axis([diagonal], DEFAULTS)[0] == diagonal

    def test_near_corners_are_pulled_onto_one_node(self) -> None:
        # Two walls whose ends miss by a few pixels — a corner that would leak.
        walls = [seg(0, 0, 200, 0, thickness=10), seg(203, 4, 203, 200, thickness=10)]
        snapped = snap_endpoints(walls, DEFAULTS)
        assert snapped[0].p2 == pytest.approx(snapped[1].p1)

    def test_a_wall_that_collapses_onto_one_node_is_discarded(self) -> None:
        assert snap_endpoints([seg(0, 0, 1, 1, thickness=40)], DEFAULTS) == []

    def test_a_near_miss_t_junction_is_closed_by_extending(self) -> None:
        spine = seg(0, 0, 400, 0, thickness=10)
        branch = seg(200, 12, 200, 300, thickness=10)
        extended = extend_to_intersections([spine, branch], DEFAULTS)
        assert extended[1].p1 == pytest.approx((200.0, 0.0))

    def test_extending_leaves_a_wall_that_is_already_far_away(self) -> None:
        spine = seg(0, 0, 400, 0, thickness=10)
        branch = seg(200, 200, 200, 400, thickness=10)
        assert extend_to_intersections([spine, branch], DEFAULTS)[1].p1 == pytest.approx((200.0, 200.0))

    def test_duplicate_walls_are_removed(self) -> None:
        walls = [seg(0, 0, 300, 0), seg(300, 0, 0, 0), seg(0, 200, 300, 200)]
        assert len(deduplicate(walls, DEFAULTS)) == 2


class TestCleanPipeline:
    def test_a_ragged_rectangle_becomes_four_closed_walls(self) -> None:
        """The shape a detector really produces: split runs, missed corners."""
        raw = [
            seg(2, 1, 300, 3, thickness=10),  # top, in two pieces
            seg(299, 2, 600, 0, thickness=10),
            seg(602, 4, 599, 400, thickness=10),  # right, leaning
            seg(598, 398, 3, 402, thickness=10),  # bottom
            seg(1, 399, 4, 2, thickness=10),  # left
            seg(120, 200, 126, 202, thickness=10),  # a speck
        ]
        cleaned = clean(raw, DEFAULTS, min_length=20)

        assert len(cleaned) == 4
        for wall in cleaned:
            angle = wall.angle_deg
            assert min(angle, abs(angle - 90.0), 180.0 - angle) < 1e-6

        # Every corner is shared, so the loop is closed.
        ends = [p for wall in cleaned for p in (wall.p1, wall.p2)]
        for point in ends:
            matches = sum(1 for other in ends if math.dist(point, other) < 1e-6)
            assert matches == 2

    def test_cleanup_of_nothing_is_nothing(self) -> None:
        assert clean([], DEFAULTS, min_length=20) == []


class TestJambStubs:
    """A doorway leaves a short return of wall at each jamb. Those returns are
    below any sane minimum wall length, but without them an opening has only
    one end and is never found as a gap at all."""

    def stub(self, x1: float, y1: float, x2: float, y2: float, thickness: float = 10.0) -> Segment:
        return Segment(x1=x1, y1=y1, x2=x2, y2=y2, thickness=thickness)

    def test_a_stub_on_a_walls_own_line_is_taken_back(self) -> None:
        wall = seg(0, 100, 300, 100, thickness=10)
        jamb = self.stub(390, 100, 405, 100, thickness=10)
        kept = readmit_stubs([wall], [jamb], DEFAULTS, 10.0, max_gap=200)
        assert len(kept) == 2

    def test_a_stub_on_a_different_line_is_left_out(self) -> None:
        wall = seg(0, 100, 300, 100, thickness=10)
        elsewhere = self.stub(390, 400, 405, 400, thickness=10)
        assert readmit_stubs([wall], [elsewhere], DEFAULTS, 10.0, max_gap=200) == [wall]

    def test_a_stub_beyond_an_opening_away_is_left_out(self) -> None:
        wall = seg(0, 100, 300, 100, thickness=10)
        far = self.stub(900, 100, 915, 100, thickness=10)
        assert readmit_stubs([wall], [far], DEFAULTS, 10.0, max_gap=200) == [wall]

    def test_a_fat_fitting_is_left_out_however_well_it_lines_up(self) -> None:
        """A round basin traced as a bar reports its diameter as a thickness."""
        wall = seg(0, 100, 300, 100, thickness=10)
        basin = self.stub(390, 100, 405, 100, thickness=40)
        assert readmit_stubs([wall], [basin], DEFAULTS, 10.0, max_gap=200) == [wall]

    def test_a_stub_of_the_wrong_thickness_is_left_out(self) -> None:
        wall = seg(0, 100, 300, 100, thickness=10)
        wrong = self.stub(390, 100, 405, 100, thickness=17)
        assert readmit_stubs([wall], [wrong], DEFAULTS, 10.0, max_gap=200) == [wall]

    def test_a_blob_is_left_out_even_at_the_right_thickness(self) -> None:
        wall = seg(0, 100, 300, 100, thickness=10)
        blob = self.stub(390, 100, 395, 100, thickness=10)  # shorter than it is thick
        assert readmit_stubs([wall], [blob], DEFAULTS, 10.0, max_gap=200) == [wall]

    def test_a_speck_is_left_out(self) -> None:
        wall = seg(0, 100, 300, 100, thickness=10)
        speck = self.stub(390, 100, 393, 100, thickness=2)
        assert readmit_stubs([wall], [speck], DEFAULTS, 10.0, max_gap=200) == [wall]

    def test_a_nib_returning_off_a_wall_is_taken_back(self) -> None:
        """The two jambs of a built-in robe are nibs, not wall continuations.

        Neither lies on any long wall's line — they return off it at a right
        angle to form the recess — so the collinear rule alone leaves every
        robe open and its bedroom boundary wrong.
        """
        wall = seg(0, 100, 600, 100, thickness=10)
        nib = self.stub(200, 100, 200, 118, thickness=10)
        kept = readmit_stubs([wall], [nib], DEFAULTS, 10.0, max_gap=200)
        assert len(kept) == 2

    def test_a_nib_that_does_not_reach_the_wall_is_left_out(self) -> None:
        wall = seg(0, 100, 600, 100, thickness=10)
        floating = self.stub(200, 160, 200, 178, thickness=10)
        assert readmit_stubs([wall], [floating], DEFAULTS, 10.0, max_gap=200) == [wall]

    def test_a_fitting_touching_a_wall_is_still_left_out(self) -> None:
        """Touching is not enough; it has to be the wall's own thickness."""
        wall = seg(0, 100, 600, 100, thickness=10)
        fitting = self.stub(200, 100, 200, 140, thickness=38)
        assert readmit_stubs([wall], [fitting], DEFAULTS, 10.0, max_gap=200) == [wall]

    def test_nothing_to_take_back_changes_nothing(self) -> None:
        wall = seg(0, 100, 300, 100)
        assert readmit_stubs([wall], [], DEFAULTS, 10.0, max_gap=200) == [wall]
        assert readmit_stubs([], [self.stub(0, 0, 20, 0)], DEFAULTS, 10.0, max_gap=200) == []

    def test_a_readmitted_stub_completes_a_doorway(self) -> None:
        """The point of the whole exercise: two ends, so a gap can be found."""
        from app.config import derive
        from app.pipeline.openings import find_gaps

        board = derive(DEFAULTS, 800, 600, 10.0)
        wall = seg(0, 100, 300, 100, thickness=10)
        jamb = self.stub(390, 100, 410, 100, thickness=10)

        assert find_gaps([wall], DEFAULTS, board) == []
        walls = readmit_stubs([wall], [jamb], DEFAULTS, 10.0, max_gap=200)
        gaps = find_gaps(walls, DEFAULTS, board)
        assert len(gaps) == 1 and gaps[0].collinear


def off_axis(seg: Segment) -> float:
    """How far a segment is from being exactly horizontal or vertical."""
    return min(abs(seg.x2 - seg.x1), abs(seg.y2 - seg.y1))


class TestCleanupKeepsWallsSquare:
    """Cleanup must never hand back a leaning wall.

    Snapping endpoints onto shared nodes deliberately pulls walls off square —
    a node is the average of the ends that met there. align_axes is what puts
    them back, and it has to do so for *any* lean snapping can produce, not
    just a small one: endpoints are snapped over a far wider distance than the
    alignment tolerance, so a lean can be too large to be clustered away.
    """

    def two_walls_meeting_off_square(self) -> list[Segment]:
        # Ends 14 px apart: inside the endpoint snap radius (1.6 x 10 px) and
        # well outside the axis-align tolerance (0.5 x 10 px), so snapping
        # leans both walls by 7 px and no clustering of coordinates can see it.
        return [
            Segment(x1=0, y1=100, x2=200, y2=100, thickness=10),
            Segment(x1=200, y1=114, x2=400, y2=114, thickness=10),
        ]

    def test_a_lean_wider_than_the_align_tolerance_is_still_squared(self) -> None:
        result = align_axes(snap_endpoints(self.two_walls_meeting_off_square(), DEFAULTS), DEFAULTS)
        assert result, "the walls should survive"
        for seg in result:
            assert off_axis(seg) == pytest.approx(0.0, abs=1e-6), f"{seg} is not square"

    def test_the_corner_stays_joined_while_being_squared(self) -> None:
        """Squaring must not undo the join it is squaring."""
        walls = [
            Segment(x1=0, y1=100, x2=200, y2=100, thickness=10),
            Segment(x1=207, y1=93, x2=207, y2=300, thickness=10),
        ]
        result = align_axes(snap_endpoints(walls, DEFAULTS), DEFAULTS)
        ends = [p for seg in result for p in (seg.p1, seg.p2)]
        shared = [p for p in ends if ends.count(p) > 1]
        assert shared, "the two walls no longer meet at a point"
        for seg in result:
            assert off_axis(seg) == pytest.approx(0.0, abs=1e-6)

    def test_the_full_cleanup_returns_only_square_walls(self) -> None:
        walls = [
            Segment(x1=0, y1=0, x2=500, y2=3, thickness=10),
            Segment(x1=497, y1=14, x2=497, y2=400, thickness=10),
            Segment(x1=490, y1=396, x2=100, y2=404, thickness=10),
            Segment(x1=106, y1=390, x2=100, y2=12, thickness=10),
        ]
        for seg in clean(walls, DEFAULTS, 30):
            assert off_axis(seg) == pytest.approx(0.0, abs=1e-6), f"{seg} is not square"


class TestSliversVersusJambs:
    """The last pass must drop what collapsed without eating door jambs."""

    def test_a_jamb_return_shorter_than_a_wall_survives(self) -> None:
        jamb = Segment(x1=0, y1=0, x2=25, y2=0, thickness=12)
        assert drop_slivers([jamb], DEFAULTS, 26) == [jamb]

    def test_a_stubby_junction_blob_does_not(self) -> None:
        """Short *and* fat: wider than it is long, so it was never a wall."""
        blob = Segment(x1=0, y1=0, x2=21, y2=0, thickness=48)
        assert drop_slivers([blob], DEFAULTS, 26) == []

    def test_a_speck_on_a_thin_walled_plan_does_not(self) -> None:
        """The aspect test alone would admit this: 7 px long, 6 px thick."""
        speck = Segment(x1=0, y1=0, x2=7, y2=0, thickness=6)
        assert drop_slivers([speck], DEFAULTS, 15) == []

    def test_a_wall_shortened_by_snapping_is_not_then_discarded(self) -> None:
        """The regression itself.

        Both walls are long enough to be walls when they arrive. Snapping
        pulls their ends together, which shortens the horizontal one below the
        minimum wall length — and re-applying that minimum at the end of
        cleanup then deleted it, taking the doorway edge with it.
        """
        walls = [
            Segment(x1=0, y1=0, x2=45, y2=0, thickness=12),
            Segment(x1=30, y1=-100, x2=30, y2=-5, thickness=12),
        ]
        result = clean(walls, DEFAULTS, 40)
        horizontals = [s for s in result if abs(s.x2 - s.x1) > abs(s.y2 - s.y1)]
        assert horizontals, f"the shortened wall was discarded: {result}"
        assert horizontals[0].length < 40, "the premise: it really is under the floor now"
