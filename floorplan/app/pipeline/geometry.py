"""Wall geometry: the segment type, and the cleanup stage that turns raw
detections into an architectural wall set.

Everything here is pure — no OpenCV, no image — so it is directly unit
testable. Coordinates are pixels of the plan region at this stage; the
conversion to millimetres happens once, at the end of the pipeline.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, replace
from typing import Iterable, Sequence

from ..config import Settings

Point = tuple[float, float]


@dataclass(frozen=True)
class Segment:
    """A wall centre line with a thickness."""

    x1: float
    y1: float
    x2: float
    y2: float
    thickness: float
    confidence: float = 0.8
    #: Fraction of the segment's swept rectangle that was actually ink.
    fill: float = 1.0

    # -- basic geometry ----------------------------------------------------
    @property
    def p1(self) -> Point:
        return (self.x1, self.y1)

    @property
    def p2(self) -> Point:
        return (self.x2, self.y2)

    @property
    def length(self) -> float:
        return math.hypot(self.x2 - self.x1, self.y2 - self.y1)

    @property
    def midpoint(self) -> Point:
        return ((self.x1 + self.x2) / 2.0, (self.y1 + self.y2) / 2.0)

    @property
    def angle_deg(self) -> float:
        """Direction in [0, 180) — a wall is the same wall reversed."""
        deg = math.degrees(math.atan2(self.y2 - self.y1, self.x2 - self.x1)) % 180.0
        return deg

    @property
    def direction(self) -> Point:
        length = self.length
        if length == 0:
            return (1.0, 0.0)
        return ((self.x2 - self.x1) / length, (self.y2 - self.y1) / length)

    @property
    def is_horizontal(self) -> bool:
        a = self.angle_deg
        return a < 45.0 or a > 135.0

    def with_points(self, p1: Point, p2: Point) -> "Segment":
        return replace(self, x1=p1[0], y1=p1[1], x2=p2[0], y2=p2[1])


def angular_difference(a: float, b: float) -> float:
    """Smallest angle between two undirected directions, in degrees."""
    diff = abs(a - b) % 180.0
    return min(diff, 180.0 - diff)


def perpendicular_distance(seg: Segment, point: Point) -> float:
    """Distance from ``point`` to the infinite line through ``seg``."""
    dx, dy = seg.direction
    return abs(dx * (point[1] - seg.y1) - dy * (point[0] - seg.x1))


def project(seg: Segment, point: Point) -> float:
    """Signed position of ``point`` along the segment's direction, from p1."""
    dx, dy = seg.direction
    return dx * (point[0] - seg.x1) + dy * (point[1] - seg.y1)


def line_intersection(a: Segment, b: Segment) -> Point | None:
    """Where two centre lines cross, treating both as infinite. None if parallel."""
    ax, ay = a.direction
    bx, by = b.direction
    denom = ax * by - ay * bx
    if abs(denom) < 1e-9:
        return None
    t = ((b.x1 - a.x1) * by - (b.y1 - a.y1) * bx) / denom
    return (a.x1 + ax * t, a.y1 + ay * t)


def segment_intersection(a: Segment, b: Segment, tol: float = 1e-6) -> Point | None:
    """Where two centre lines cross within both spans. Endpoint touches count."""
    if a.length <= tol or b.length <= tol:
        return None  # a point is not a wall
    point = line_intersection(a, b)
    if point is None:
        return None
    for seg in (a, b):
        t = project(seg, point)
        if t < -tol or t > seg.length + tol:
            return None
    return point


def is_collinear(a: Segment, b: Segment, settings: Settings) -> bool:
    """Do both segments lie on one line, within a fraction of a wall's thickness?

    The decisive test is how far the shorter segment's endpoints sit from the
    longer one's line, not the angle between them: a fifty-pixel stub that
    drifts three pixels is nearly four degrees off, yet it is plainly part of
    the same wall. The angle only has to be sane enough to rule out a
    perpendicular stub that happens to start on the line.
    """
    if angular_difference(a.angle_deg, b.angle_deg) > settings.collinear_angle_tol_deg:
        return False
    reference, other = (a, b) if a.length >= b.length else (b, a)
    offset_tol = settings.collinear_offset_ratio * max(a.thickness, b.thickness)
    return (
        perpendicular_distance(reference, other.p1) <= offset_tol
        and perpendicular_distance(reference, other.p2) <= offset_tol
    )


def distance_to_segment(seg: Segment, point: Point) -> float:
    """Distance from ``point`` to the segment itself, not its infinite line."""
    length = seg.length
    if length == 0:
        return math.dist(seg.p1, point)
    t = max(0.0, min(length, project(seg, point)))
    dx, dy = seg.direction
    return math.dist((seg.x1 + dx * t, seg.y1 + dy * t), point)


def collinear_gap(a: Segment, b: Segment) -> float:
    """End-to-end gap along ``a``'s direction. Negative when they overlap."""
    a0, a1 = 0.0, a.length
    b0, b1 = sorted((project(a, b.p1), project(a, b.p2)))
    if b0 > a1:
        return b0 - a1
    if a0 > b1:
        return a0 - b1
    return -min(a1, b1) + max(a0, b0)


# ---------------------------------------------------------------------------
# Cleanup stages
# ---------------------------------------------------------------------------


class _UnionFind:
    def __init__(self, n: int) -> None:
        self._parent = list(range(n))

    def find(self, i: int) -> int:
        while self._parent[i] != i:
            self._parent[i] = self._parent[self._parent[i]]
            i = self._parent[i]
        return i

    def union(self, i: int, j: int) -> None:
        ri, rj = self.find(i), self.find(j)
        if ri != rj:
            self._parent[rj] = ri

    def groups(self) -> list[list[int]]:
        buckets: dict[int, list[int]] = {}
        for i in range(len(self._parent)):
            buckets.setdefault(self.find(i), []).append(i)
        return list(buckets.values())


def drop_short(segments: Iterable[Segment], min_length: float) -> list[Segment]:
    """Remove specks and stubs that no wall could be."""
    return [s for s in segments if s.length >= min_length]


def drop_slivers(
    segments: Iterable[Segment], settings: Settings, min_length: float
) -> list[Segment]:
    """Remove what cleaning collapsed, judged per segment rather than globally.

    Applying the full minimum wall length again at the end of cleanup throws
    away the wrong things. Everything reaching this point was long enough to
    be a wall when it arrived; anything now shorter either collapsed under
    snapping, or is a short return of wall at a doorway jamb — and those are
    exactly what marks where an opening starts and stops, so losing them costs
    a door and then a room.

    Two tests, and a bar has to pass both. **A wall is longer than it is
    thick** drops the stubby blob left where two thick walls crossed — 21 px
    long and 48 px thick, never a wall at all — while keeping a 25 px jamb
    return on a 12 px wall. And a floor proportional to the minimum wall
    length keeps the first test honest on a low-resolution plan, where walls
    are only 6 px thick and "longer than it is thick" would admit every
    speck.
    """
    return [
        s
        for s in segments
        if s.length >= max(s.thickness * settings.sliver_aspect,
                           min_length * settings.sliver_length_ratio)
    ]


def snap_to_axis(segments: Iterable[Segment], settings: Settings) -> list[Segment]:
    """Square up walls that are essentially horizontal or vertical.

    Estate-agent plans are drawn orthogonally; any lean is an artefact of
    tracing, and leaving it in makes every downstream tolerance fuzzier.

    Two tests, either of which is enough. The angle test catches long walls,
    where a couple of pixels of drift is a negligible angle. The deviation test
    catches short stubs, where three pixels across a fifty-pixel stub is nearly
    four degrees yet still less than the wall is thick — a lean nobody drew.
    """
    out: list[Segment] = []
    for s in segments:
        angle = s.angle_deg
        dx = abs(s.x2 - s.x1)
        dy = abs(s.y2 - s.y1)
        deviation_tol = max(settings.axis_snap_min_px, settings.axis_snap_thickness_ratio * s.thickness)

        horizontal = min(angle, 180.0 - angle) <= settings.axis_snap_deg or (
            dy <= deviation_tol and dx > dy
        )
        vertical = abs(angle - 90.0) <= settings.axis_snap_deg or (
            dx <= deviation_tol and dy > dx
        )

        if horizontal and not vertical:
            y = (s.y1 + s.y2) / 2.0
            out.append(s.with_points((s.x1, y), (s.x2, y)))
        elif vertical and not horizontal:
            x = (s.x1 + s.x2) / 2.0
            out.append(s.with_points((x, s.y1), (x, s.y2)))
        else:
            out.append(s)
    return out


def merge_collinear(segments: Sequence[Segment], settings: Settings) -> list[Segment]:
    """Fuse collinear segments that touch or overlap into single walls.

    The gap tolerance is deliberately about one wall thickness: a doorway is
    also a collinear gap, and merging across it would erase the opening the
    room detector relies on.
    """
    n = len(segments)
    if n < 2:
        return list(segments)

    uf = _UnionFind(n)
    for i in range(n):
        for j in range(i + 1, n):
            a, b = segments[i], segments[j]
            if not is_collinear(a, b, settings):
                continue
            gap_tol = settings.merge_gap_ratio * max(a.thickness, b.thickness)
            if collinear_gap(a, b) <= gap_tol:
                uf.union(i, j)

    merged: list[Segment] = []
    for group in uf.groups():
        members = [segments[i] for i in group]
        if len(members) == 1:
            merged.append(members[0])
            continue
        merged.append(_fuse(members))
    return merged


def _fuse(members: Sequence[Segment]) -> Segment:
    """Combine collinear segments into the one that spans them all."""
    # Use the longest member as the reference direction: it has the best
    # angular estimate.
    anchor = max(members, key=lambda s: s.length)
    projections: list[tuple[float, Point]] = []
    for seg in members:
        for point in (seg.p1, seg.p2):
            projections.append((project(anchor, point), point))
    projections.sort(key=lambda item: item[0])
    lo_t, _ = projections[0]
    hi_t, _ = projections[-1]

    dx, dy = anchor.direction
    start = (anchor.x1 + dx * lo_t, anchor.y1 + dy * lo_t)
    end = (anchor.x1 + dx * hi_t, anchor.y1 + dy * hi_t)

    total = sum(s.length for s in members) or 1.0
    thickness = sum(s.thickness * s.length for s in members) / total
    confidence = sum(s.confidence * s.length for s in members) / total
    fill = sum(s.fill * s.length for s in members) / total
    return Segment(
        x1=start[0],
        y1=start[1],
        x2=end[0],
        y2=end[1],
        thickness=thickness,
        confidence=confidence,
        fill=fill,
    )


def snap_endpoints(segments: Sequence[Segment], settings: Settings) -> list[Segment]:
    """Pull endpoints that nearly coincide onto one shared node.

    Corners come out of the detector a few pixels apart because the opening
    kernel rounds them off. Without this, rooms leak at every corner.
    """
    if not segments:
        return []
    tolerance = settings.endpoint_snap_ratio * _median_thickness(segments)

    nodes: list[Point] = []
    for seg in segments:
        nodes.extend((seg.p1, seg.p2))

    uf = _UnionFind(len(nodes))
    for i in range(len(nodes)):
        for j in range(i + 1, len(nodes)):
            if math.dist(nodes[i], nodes[j]) <= tolerance:
                uf.union(i, j)

    representative: dict[int, Point] = {}
    for group in uf.groups():
        xs = sum(nodes[i][0] for i in group) / len(group)
        ys = sum(nodes[i][1] for i in group) / len(group)
        for i in group:
            representative[i] = (xs, ys)

    out: list[Segment] = []
    for index, seg in enumerate(segments):
        p1 = representative[index * 2]
        p2 = representative[index * 2 + 1]
        if math.dist(p1, p2) < 1e-6:
            # The whole segment collapsed onto one node; it was noise.
            continue
        out.append(seg.with_points(p1, p2))
    return out


def extend_to_intersections(segments: Sequence[Segment], settings: Settings) -> list[Segment]:
    """Close near-miss T-junctions by extending an endpoint onto the crossing wall.

    Detection stops a wall short of the one it meets, because the opening
    kernel eats the corner. A room boundary with a two-pixel hole is not a
    room, so each dangling end within tolerance is pulled onto its neighbour.
    """
    if not segments:
        return []
    tolerance = settings.junction_extend_ratio * _median_thickness(segments)
    result = list(segments)

    for index, seg in enumerate(result):
        p1, p2 = seg.p1, seg.p2
        for other_index, other in enumerate(result):
            if other_index == index:
                continue
            if angular_difference(seg.angle_deg, other.angle_deg) < 20.0:
                continue  # near-parallel walls do not form a junction
            crossing = line_intersection(seg, other)
            if crossing is None:
                continue
            # The crossing must lie on (or just past the end of) the other wall.
            t_other = project(other, crossing)
            if t_other < -tolerance or t_other > other.length + tolerance:
                continue
            if math.dist(p1, crossing) <= tolerance and project(seg, crossing) < seg.length / 2:
                p1 = crossing
            if math.dist(p2, crossing) <= tolerance and project(seg, crossing) > seg.length / 2:
                p2 = crossing
        result[index] = seg.with_points(p1, p2)

    return [s for s in result if s.length > 1e-6]


def align_axes(segments: Sequence[Segment], settings: Settings) -> list[Segment]:
    """Put every node on a shared set of x and y lines, squarely.

    Snapping endpoints to shared nodes fixes the corners but leaves the walls
    leaning, because a node is the average of the ends that met there. This
    puts them back square.

    It is a constraint solve rather than a tolerance, and that distinction is
    the whole point. Clustering coordinates that happen to lie within some
    distance of each other cannot guarantee anything: ``snap_endpoints`` is
    allowed to move an end by ``endpoint_snap_ratio`` thicknesses, which is
    wider than the clustering tolerance, so the lean it introduces can simply
    be too large to be clustered away and survives to the output.

    So the axis requirement is stated directly instead. Each endpoint belongs
    to a node; a horizontal wall's two nodes are *required* to share one y, a
    vertical wall's two nodes to share one x. Those requirements are unioned
    together, every coordinate in a group takes the group's mean, and the
    result is exactly orthogonal by construction — no tolerance involved.

    Tolerance still has a job, but a smaller one: after the constraint groups
    are formed, groups whose values are within ``axis_align_thickness_ratio``
    of a thickness are merged, so walls that were nearly collinear end up
    exactly collinear. Merging groups only ever adds equalities, so it cannot
    break the orthogonality the solve just established.
    """
    if not segments:
        return []
    tolerance = settings.axis_align_thickness_ratio * _median_thickness(segments)

    # Endpoints that snap_endpoints put at the same place are one node.
    node_of: dict[Point, int] = {}
    nodes: list[Point] = []
    for seg in segments:
        for point in (seg.p1, seg.p2):
            if point not in node_of:
                node_of[point] = len(nodes)
                nodes.append(point)

    # One union-find per axis, over nodes.
    ux, uy = _UnionFind(len(nodes)), _UnionFind(len(nodes))
    for seg in segments:
        a, b = node_of[seg.p1], node_of[seg.p2]
        if abs(seg.x2 - seg.x1) >= abs(seg.y2 - seg.y1):
            uy.union(a, b)  # horizontal: one y between them
        else:
            ux.union(a, b)  # vertical: one x between them

    def solve(uf: _UnionFind, coord: int) -> dict[int, float]:
        """Give every node the mean of its constraint group, then merge groups
        that are close enough to be the same line."""
        groups = uf.groups()
        values = [sum(nodes[i][coord] for i in g) / len(g) for g in groups]

        order = sorted(range(len(groups)), key=lambda k: values[k])
        merged: dict[int, float] = {}
        run: list[int] = []
        for k in order:
            if run and values[k] - values[run[-1]] > tolerance:
                centre = sum(values[m] for m in run) / len(run)
                merged.update({m: centre for m in run})
                run = []
            run.append(k)
        if run:
            centre = sum(values[m] for m in run) / len(run)
            merged.update({m: centre for m in run})

        return {i: merged[k] for k, g in enumerate(groups) for i in g}

    xs, ys = solve(ux, 0), solve(uy, 1)

    out: list[Segment] = []
    for seg in segments:
        a, b = node_of[seg.p1], node_of[seg.p2]
        aligned = seg.with_points((xs[a], ys[a]), (xs[b], ys[b]))
        if aligned.length > 1e-9:
            out.append(aligned)
    return out


def deduplicate(segments: Sequence[Segment], settings: Settings) -> list[Segment]:
    """Drop segments that are the same wall as one already kept."""
    kept: list[Segment] = []
    for seg in segments:
        duplicate = False
        for existing in kept:
            if not is_collinear(existing, seg, settings):
                continue
            tol = settings.collinear_offset_ratio * max(existing.thickness, seg.thickness)
            if (
                math.dist(existing.p1, seg.p1) <= tol and math.dist(existing.p2, seg.p2) <= tol
            ) or (math.dist(existing.p1, seg.p2) <= tol and math.dist(existing.p2, seg.p1) <= tol):
                duplicate = True
                break
        if not duplicate:
            kept.append(seg)
    return kept


def _median_thickness(segments: Sequence[Segment]) -> float:
    values = sorted(s.thickness for s in segments)
    if not values:
        return 1.0
    mid = len(values) // 2
    if len(values) % 2:
        return values[mid]
    return (values[mid - 1] + values[mid]) / 2.0


def readmit_stubs(
    walls: Sequence[Segment],
    stubs: Sequence[Segment],
    settings: Settings,
    derived_thickness: float,
    max_gap: float,
) -> list[Segment]:
    """Take back the short bars that are really the far jamb of an opening.

    A doorway does not end the wall, it interrupts it: the drawing leaves a
    short return of wall on the far side, and that return is what says where
    the opening stops. Those returns are shorter than any sane minimum wall
    length, so the length filter throws them away — and then the opening has
    only one end, no gap is found, and no door is ever detected there.

    A stub has to be the same thickness as the wall it attaches to, and then
    connect to that wall in one of two ways:

    * *Continuing it.* On the wall's own centre line, an opening's width away —
      the far jamb of a doorway.
    * *Meeting it.* One end touching the wall. This is the nib that returns off
      a wall to form a recess: the two jambs of a built-in robe are exactly
      this, and neither of them lies on any long wall's line, so the first rule
      alone leaves every robe open and its bedroom boundary wrong.

    The thickness test is what keeps the fittings out. Basins, baths and toilet
    pans are short too, and a round one traced as a bar reports a thickness of
    its whole diameter — so without it they are admitted as walls and the plan
    sprouts fat blobs where the bathrooms are.
    """
    if not walls or not stubs:
        return list(walls)

    minimum = settings.stub_min_thickness_ratio * derived_thickness
    ceiling = settings.stub_max_thickness_ratio * derived_thickness
    touch = settings.stub_touch_thickness_ratio * derived_thickness

    kept: list[Segment] = []
    for stub in stubs:
        if stub.length < minimum or stub.thickness > ceiling:
            continue
        # A bar, not a blob.
        if stub.length < stub.thickness * settings.stub_min_aspect:
            continue
        for wall in walls:
            ratio = stub.thickness / max(1e-6, wall.thickness)
            if not (1.0 / settings.stub_thickness_match <= ratio <= settings.stub_thickness_match):
                continue

            if is_collinear(wall, stub, settings):
                if 0 < collinear_gap(wall, stub) <= max_gap:
                    kept.append(stub)
                    break
                continue

            # A nib returning off the wall: one end sits on it.
            if min(distance_to_segment(wall, stub.p1), distance_to_segment(wall, stub.p2)) <= touch:
                kept.append(stub)
                break

    if not kept:
        return list(walls)
    return deduplicate([*walls, *snap_to_axis(kept, settings)], settings)


def drop_detached_furniture(
    segments: Sequence[Segment], tolerance: float
) -> tuple[list[Segment], list[Segment]]:
    """Separate the building from the page it is printed on.

    An estate-agent sheet carries an agency logo, and a logo is drawn with
    strokes as heavy as a wall — so it survives every filter aimed at text and
    arrives here as a little cluster of perfectly good walls. On one test plan
    that cluster is eight of them.

    It does two kinds of damage. It draws a fragment of nonsense building below
    the plan, and, less obviously, it stretches the walls' bounding box down
    over the disclaimer — which is what the *text* filter uses to decide what
    is on the plan and what is on the page. So the logo being mistaken for
    walls is why the disclaimer survives as a label.

    A building is one connected thing, so the largest connected run of wall is
    the building, and its bounding box is where the floor plan is. Anything
    lying wholly outside those coordinates is on the page rather than in the
    house, and goes — walls here, and the text filter is given the same box so
    a disclaimer is judged against the building instead of against whatever the
    logo dragged the bounds out to.

    The cost of stating it this plainly is that a genuinely detached outbuilding,
    drawn clear of the house and touching nothing, would go too. No test plan
    has one, and the alternative — keeping anything above some size — is what
    lets a large logo through.

    Returns the walls kept and the walls dropped, so the caller can report it.
    """
    if len(segments) < 2:
        return list(segments), []

    groups = _connected_groups(segments, tolerance)
    if len(groups) < 2:
        return list(segments), []

    length = lambda group: sum(segments[i].length for i in group)  # noqa: E731
    groups.sort(key=length, reverse=True)
    main, rest = groups[0], groups[1:]
    main_length = length(main)
    min_x, min_y, max_x, max_y = _bounds([segments[i] for i in main])

    del main_length  # the rule is positional; size is not consulted

    dropped: set[int] = set()
    for group in rest:
        gx1, gy1, gx2, gy2 = _bounds([segments[i] for i in group])
        if gx2 < min_x or gx1 > max_x or gy2 < min_y or gy1 > max_y:
            dropped.update(group)

    return (
        [s for i, s in enumerate(segments) if i not in dropped],
        [s for i, s in enumerate(segments) if i in dropped],
    )


def building_bounds(
    segments: Sequence[Segment], tolerance: float
) -> tuple[float, float, float, float]:
    """Where the floor plan is: the bounding box of the largest connected run.

    Used for the walls and for the text, so both are judged against the
    building rather than against everything that happened to be detected.
    """
    groups = _connected_groups(segments, tolerance)
    main = max(groups, key=lambda g: sum(segments[i].length for i in g))
    return _bounds([segments[i] for i in main])


def _bounds(segments: Sequence[Segment]) -> tuple[float, float, float, float]:
    xs = [v for s in segments for v in (s.x1, s.x2)]
    ys = [v for s in segments for v in (s.y1, s.y2)]
    return min(xs), min(ys), max(xs), max(ys)


def _connected_groups(segments: Sequence[Segment], tolerance: float) -> list[list[int]]:
    """Walls grouped by what touches what.

    Touching means an end of one lands on any part of the other, not just on
    its end — walls meet at T-junctions far more often than at corners, and a
    test that only compares endpoints reports a single house as a few dozen
    separate pieces.
    """
    uf = _UnionFind(len(segments))
    for i in range(len(segments)):
        for j in range(i + 1, len(segments)):
            a, b = segments[i], segments[j]
            if (
                min(distance_to_segment(b, a.p1), distance_to_segment(b, a.p2)) <= tolerance
                or min(distance_to_segment(a, b.p1), distance_to_segment(a, b.p2)) <= tolerance
            ):
                uf.union(i, j)
    return uf.groups()


def clean(segments: Sequence[Segment], settings: Settings, min_length: float) -> list[Segment]:
    """The full cleanup pass, in the order the stages depend on each other."""
    result = drop_short(segments, min_length)
    result = snap_to_axis(result, settings)
    result = merge_collinear(result, settings)
    result = snap_endpoints(result, settings)
    result = extend_to_intersections(result, settings)
    # Extending can make two walls collinear-adjacent that were not before.
    result = merge_collinear(result, settings)
    result = align_axes(result, settings)
    result = deduplicate(result, settings)
    return drop_slivers(result, settings, min_length)


def find_intersections(segments: Sequence[Segment]) -> list[Point]:
    """Every point where two walls cross. Used for reporting and tests."""
    points: list[Point] = []
    for i in range(len(segments)):
        for j in range(i + 1, len(segments)):
            point = segment_intersection(segments[i], segments[j])
            if point is not None:
                points.append(point)
    return points
