"""Step 5: work out what the gaps in the walls are, and heal the walls.

A detector sees a wall with a door in it as *two* walls. That is wrong twice
over: the wall outline comes out fragmented, so rooms leak at every opening,
and the door — the thing that explains the gap — is not recorded anywhere.

This stage looks at each gap and asks what it is, using the drawing itself:

* **Sliding door** — *two* lines run across the gap, at different offsets across
  the wall, each covering part of it and overlapping in the middle. That is two
  panels passing one another, and it is the one thing that looks like a window
  to a test that only asks whether *a* line spans the gap.
* **Window** — a *single* straight line runs across the gap, parallel to the wall.
  A wall never actually stops for a window; it is only drawn differently, as a
  pair of thin reveal lines or a lighter band. The test is that shape, not any
  particular shade, so it holds across drawing styles.
* **Door** — an arc is drawn across the gap, hinged at one end. Looking for
  pixels at a constant radius from a hinge candidate finds it.
* **Garage door** — a dashed rectangle is drawn across the opening. The tell is
  not that a line is there — a window has one too — but that the line is
  *broken*, at a regular period, all the way across. So the dashes are counted
  rather than the ink measured.
* **Opening** — a gap with none of those: a cased opening, or an archway.

Once a gap is identified the two walls either side are fused back into one
continuous wall, and the opening is recorded against it as a position and a
width. Rooms are then bounded by whole walls rather than by fragments, which is
what makes the room outlines come out right.

Gaps that are *not* collinear — two wall ends meeting around a corner, where a
plan simply leaves the boundary open — cannot be part of one wall. Those are
returned separately as closures, used to bound rooms and nothing else.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Literal

import cv2
import numpy as np

from ..config import Derived, Settings
from .geometry import Segment, _fuse, is_collinear, project

Point = tuple[float, float]

OpeningKind = Literal["door", "sliding", "garage", "window", "opening"]


@dataclass
class _DoorSwing:
    """How a door was found to be hung."""

    score: float
    leaf: float
    arc: float
    #: True when the hinge is at the gap's first endpoint.
    hinge_at_p1: bool
    #: +1 or -1 along the wall normal: which side the leaf opens to.
    swing_sign: float


@dataclass
class Opening:
    kind: OpeningKind
    #: Index into the *merged* wall list.
    wall_index: int
    #: Distance in pixels from the merged wall's start to the opening's centre.
    position: float
    width: float
    confidence: float
    #: Doors only. Which jamb carries the hinge, in the healed wall's own
    #: direction, and which side the leaf swings to.
    hinge_at_start: bool = True
    swing_degrees: float = 90.0


@dataclass
class OpeningResult:
    #: Walls with door and window gaps healed back into continuous runs.
    walls: list[Segment] = field(default_factory=list)
    openings: list[Opening] = field(default_factory=list)
    #: Non-collinear boundary gaps. Bound rooms; never become walls.
    closures: list[Segment] = field(default_factory=list)

    @property
    def doors(self) -> list[Opening]:
        return [o for o in self.openings if o.kind == "door"]

    @property
    def windows(self) -> list[Opening]:
        return [o for o in self.openings if o.kind == "window"]

    @property
    def sliding(self) -> list[Opening]:
        return [o for o in self.openings if o.kind == "sliding"]

    @property
    def garages(self) -> list[Opening]:
        return [o for o in self.openings if o.kind == "garage"]


@dataclass
class _WallEnd:
    point: Point
    segment: int
    thickness: float
    node: int


@dataclass
class _Gap:
    a: int
    b: int
    p1: Point
    p2: Point
    width: float
    collinear: bool


def resolve_openings(
    segments: list[Segment],
    ink: np.ndarray,
    settings: Settings,
    derived: Derived,
    marks: np.ndarray | None = None,
) -> OpeningResult:
    """Classify the gaps, heal the walls, and report both.

    ``marks`` is the looser view of the page — everything drawn, not only what
    is dark enough to be a wall. Without it a lightly-drawn window leaves no
    trace and its wall stays broken.
    """
    if not segments:
        return OpeningResult()

    # The door symbol is drawn *around* the walls, never on them. Taking the
    # walls back out of the ink stops a wall that meets a jamb at a right angle
    # from reading as a door leaf — which is the one thing on a floor plan that
    # looks exactly like one.
    detail = _non_wall_ink(ink, segments, derived)

    gaps = find_gaps(segments, settings, derived, marks)
    closures = [
        Segment(
            x1=g.p1[0], y1=g.p1[1], x2=g.p2[0], y2=g.p2[1],
            thickness=max(segments[g.a].thickness, segments[g.b].thickness),
            confidence=0.0, fill=0.0,
        )
        for g in gaps
        if not g.collinear
    ]

    classified: list[tuple[_Gap, OpeningKind, float, _DoorSwing | None]] = []
    for gap in gaps:
        if not gap.collinear:
            continue
        kind, confidence, swing = classify_gap(gap, detail, settings, derived, marks)
        classified.append((gap, kind, confidence, swing))

    walls, index_map = _merge_across(segments, [g for g, _, _, _ in classified])

    openings: list[Opening] = []
    for gap, kind, confidence, swing in classified:
        wall_index = index_map.get(gap.a)
        if wall_index is None or wall_index != index_map.get(gap.b):
            continue  # the merge did not take; leave the gap as a plain hole
        wall = walls[wall_index]
        centre = ((gap.p1[0] + gap.p2[0]) / 2.0, (gap.p1[1] + gap.p2[1]) / 2.0)

        hinge_at_start = True
        swing_degrees = 90.0
        if swing is not None:
            hinge = gap.p1 if swing.hinge_at_p1 else gap.p2
            other = gap.p2 if swing.hinge_at_p1 else gap.p1
            # The gap's own p1/p2 order is arbitrary; re-express the hinge and
            # the swing side in the healed wall's direction, which is what the
            # renderer works in.
            hinge_at_start = project(wall, hinge) <= project(wall, other)
            wx, wy = wall.direction
            leaf_x = -(other[1] - hinge[1])
            leaf_y = other[0] - hinge[0]
            norm = math.hypot(leaf_x, leaf_y) or 1.0
            leaf_x, leaf_y = (leaf_x / norm) * swing.swing_sign, (leaf_y / norm) * swing.swing_sign
            # Positive when the leaf opens to the wall's left-hand normal.
            swing_degrees = 90.0 if (wx * leaf_y - wy * leaf_x) > 0 else -90.0

        openings.append(
            Opening(
                kind=kind,
                wall_index=wall_index,
                position=max(0.0, min(wall.length, project(wall, centre))),
                width=gap.width,
                confidence=confidence,
                hinge_at_start=hinge_at_start,
                swing_degrees=swing_degrees,
            )
        )

    return OpeningResult(walls=walls, openings=openings, closures=closures)


def _non_wall_ink(ink: np.ndarray, segments: list[Segment], derived: Derived) -> np.ndarray:
    """Ink with the detected walls painted out."""
    walls_mask = np.zeros_like(ink)
    pad = max(2, int(round(derived.wall_thickness_px * 0.35)))
    for seg in segments:
        cv2.line(
            walls_mask,
            (int(round(seg.x1)), int(round(seg.y1))),
            (int(round(seg.x2)), int(round(seg.y2))),
            255,
            max(1, int(round(seg.thickness)) + pad),
        )
    return cv2.bitwise_and(ink, cv2.bitwise_not(walls_mask))


# ---------------------------------------------------------------------------
# Finding the gaps
# ---------------------------------------------------------------------------


def find_gaps(
    segments: list[Segment],
    settings: Settings,
    derived: Derived,
    marks: np.ndarray | None = None,
) -> list[_Gap]:
    """Pairs of wall ends that face each other across a short gap.

    Facing means neither end has a wall leaving it in the other's direction —
    the gap is a hole in the boundary rather than the inside of a corner.

    Three guards: the gap is no wider than a door unless both ends are loose
    (a garage door); the gap runs roughly along an axis, so it cannot cut a
    small room corner to corner; and each wall end is used once, taking the
    shortest candidates first, so nothing fans out.

    The width guard is lifted for a gap that a straight line still spans. That
    is a window, and a window can be four metres long: the wall line never
    actually stops there, so there is no reason to insist the gap be door-sized
    before healing it. A *dashed* line spanning it lifts the guard for the same
    reason, and is a garage door — wider again.
    """
    ceiling = settings.max_opening_frac * derived.min_dim
    doorway_px = min(ceiling, settings.max_doorway_thickness_ratio * derived.wall_thickness_px)
    opening_px = min(ceiling, settings.max_opening_thickness_ratio * derived.wall_thickness_px)

    ends, node_directions, node_degree = _wall_ends(segments, derived)
    if len(ends) < 2:
        return []

    axis_tol = math.cos(math.radians(settings.bridge_axis_tol_deg))
    free_tol = math.cos(math.radians(settings.bridge_free_angle_deg))

    def facing(end: _WallEnd, towards: Point) -> bool:
        dx = towards[0] - end.point[0]
        dy = towards[1] - end.point[1]
        norm = math.hypot(dx, dy)
        if norm == 0:
            return False
        ux, uy = dx / norm, dy / norm
        if max(abs(ux), abs(uy)) < axis_tol:
            return False
        return all(ux * ox + uy * oy <= free_tol for ox, oy in node_directions[end.node])

    candidates: list[tuple[float, int, int]] = []
    for i, a in enumerate(ends):
        for j in range(i + 1, len(ends)):
            b = ends[j]
            if a.segment == b.segment or a.node == b.node:
                continue
            distance = math.dist(a.point, b.point)
            loose = node_degree[a.node] == 1 and node_degree[b.node] == 1
            limit = opening_px if loose else doorway_px
            if distance <= 0 or distance > ceiling:
                continue
            # Narrower than the wall is thick is not an opening. Nothing is
            # built that small; it is two fragments of one wall whose ends
            # missed each other, or two nodes that should have been one. Left
            # in, such a gap is worse than useless: each wall end may be used
            # once, so a four-pixel sliver can take an end that a real doorway
            # or window needed and the real opening is then never formed.
            if distance < settings.min_gap_thickness_ratio * derived.wall_thickness_px:
                continue
            # Facing first. It is pure arithmetic and it throws out most pairs,
            # whereas the two tests below walk the image; on a plan with a
            # hundred wall ends the ordering is the difference between reading
            # a few hundred gaps and a few thousand.
            if not facing(a, b.point) or not facing(b, a.point):
                continue
            if (
                distance > limit
                and _line_continues(a.point, b.point, marks, settings, derived)
                < settings.window_min_run
                and not _is_garage(a.point, b.point, marks, settings, derived)
            ):
                continue
            candidates.append((distance, i, j))

    candidates.sort()
    used: set[int] = set()
    gaps: list[_Gap] = []
    for distance, i, j in candidates:
        if i in used or j in used:
            continue
        used.add(i)
        used.add(j)
        a, b = ends[i], ends[j]
        gaps.append(
            _Gap(
                a=a.segment,
                b=b.segment,
                p1=a.point,
                p2=b.point,
                width=distance,
                collinear=is_collinear(segments[a.segment], segments[b.segment], settings),
            )
        )
    return gaps


def _wall_ends(
    segments: list[Segment], derived: Derived
) -> tuple[list[_WallEnd], list[list[Point]], list[int]]:
    """Index every wall end onto a shared node, with the directions leaving it."""
    tolerance = max(2.0, 0.25 * derived.wall_thickness_px)
    node_points: list[Point] = []
    node_directions: list[list[Point]] = []
    node_degree: list[int] = []

    def node_for(point: Point) -> int:
        for index, existing in enumerate(node_points):
            if math.dist(existing, point) <= tolerance:
                return index
        node_points.append(point)
        node_directions.append([])
        node_degree.append(0)
        return len(node_points) - 1

    ends: list[_WallEnd] = []
    for index, seg in enumerate(segments):
        dx, dy = seg.direction
        for point, outgoing in ((seg.p1, (dx, dy)), (seg.p2, (-dx, -dy))):
            node = node_for(point)
            node_directions[node].append(outgoing)
            node_degree[node] += 1
            ends.append(_WallEnd(point=point, segment=index, thickness=seg.thickness, node=node))
    return ends, node_directions, node_degree


# ---------------------------------------------------------------------------
# Classifying a gap
# ---------------------------------------------------------------------------


def _line_continues(
    p1: Point,
    p2: Point,
    marks: np.ndarray | None,
    settings: Settings,
    derived: Derived,
) -> float:
    """How far an unbroken straight line carries on across the gap, 0–1.

    This is the shape a window makes and the shape a doorway does not: the wall
    line never actually stops at a window, it is merely drawn differently —
    lighter, or as a pair of thin reveal lines. So the question is not what
    colour anything is, it is whether *something straight and parallel to the
    wall* spans the opening.

    Several lines parallel to the wall are walked, one per offset across the
    wall's own width, and the longest unbroken run of marks on any of them is
    returned as a fraction of the gap. A door leaf lying across the opening
    fails this: it is not parallel, so no single offset line stays on it.
    """
    if marks is None:
        return 0.0
    length = math.dist(p1, p2)
    if length <= 1:
        return 0.0

    ux = (p2[0] - p1[0]) / length
    uy = (p2[1] - p1[1]) / length
    nx, ny = -uy, ux

    steps = max(6, int(length))
    half = max(1.0, derived.wall_thickness_px * 0.6)
    offsets = np.linspace(-half, half, max(3, int(half * 2) + 1))
    # A run may skip a pixel or two: line art is not solid at every sample.
    tolerance = max(1, int(steps * settings.window_run_gap_tolerance))

    best = 0
    for offset in offsets:
        run = 0
        longest = 0
        missed = 0
        for i in range(steps + 1):
            t = length * i / steps
            x = p1[0] + ux * t + nx * offset
            y = p1[1] + uy * t + ny * offset
            if _sample(marks, x, y):
                run += 1
                missed = 0
                longest = max(longest, run)
            else:
                missed += 1
                if missed > tolerance:
                    run = 0
                else:
                    run += 1
        best = max(best, longest)
    return best / (steps + 1)


def _panel_intervals(
    gap: _Gap, marks: np.ndarray | None, settings: Settings, derived: Derived
) -> list[tuple[float, float, float, float]]:
    """The longest inked stretch along the gap, once per offset across the wall.

    Returns (offset, start, end, length) with the three spans as fractions of
    the gap. One line down the middle of the opening shows up as near-identical
    full-length stretches at every offset; two panels passing each other show
    up as two partial stretches, at different offsets, covering different parts.
    """
    if marks is None:
        return []
    length = math.dist(gap.p1, gap.p2)
    if length < 4:
        return []

    ux = (gap.p2[0] - gap.p1[0]) / length
    uy = (gap.p2[1] - gap.p1[1]) / length
    nx, ny = -uy, ux
    steps = max(10, int(length))
    tolerance = max(1, int(steps * settings.window_run_gap_tolerance))
    reach = derived.wall_thickness_px * settings.sliding_band_ratio

    out: list[tuple[float, float, float, float]] = []
    for offset in np.linspace(-reach, reach, settings.sliding_offset_samples):
        best = (0, 0, 0)
        run = missed = start = 0
        for i in range(steps + 1):
            t = length * i / steps
            x = gap.p1[0] + ux * t + nx * offset
            y = gap.p1[1] + uy * t + ny * offset
            if _sample(marks, x, y, 0):
                if run == 0:
                    start = i
                run += 1
                missed = 0
                if run > best[0]:
                    best = (run, start, i)
            else:
                missed += 1
                run = 0 if missed > tolerance else run + 1
        if best[0] > 0:
            out.append((offset, best[1] / steps, best[2] / steps, best[0] / (steps + 1)))
    return out


def _sliding_span(
    gap: _Gap, marks: np.ndarray | None, settings: Settings, derived: Derived, run: float
) -> float:
    """How much of the gap two offset panels cover between them, 0 if not two.

    A single line spanning the whole opening is a window, so that case is ruled
    out first. What is left has to be two stretches that are genuinely apart
    across the wall, cover different parts of the opening, overlap where the
    panels pass, and together account for most of it.
    """
    if run >= settings.sliding_max_line_run:
        return 0.0

    partial = [
        interval
        for interval in _panel_intervals(gap, marks, settings, derived)
        if settings.sliding_panel_min_run <= interval[3] <= settings.sliding_panel_max_run
    ]
    apart = derived.wall_thickness_px * settings.sliding_panel_offset_ratio

    best = 0.0
    for i in range(len(partial)):
        for j in range(i + 1, len(partial)):
            a, b = partial[i], partial[j]
            if abs(a[0] - b[0]) < apart:
                continue  # the same panel seen twice
            if abs((a[1] + a[2]) / 2 - (b[1] + b[2]) / 2) < settings.sliding_panel_shift:
                continue  # covering the same stretch, so not passing one another
            if min(a[2], b[2]) - max(a[1], b[1]) <= 0:
                continue  # they never meet, so they do not close the opening
            span = max(a[2], b[2]) - min(a[1], b[1])
            if span >= settings.sliding_min_span:
                best = max(best, min(span, 1.0))
    return best


def _dash_count(
    p1: Point, p2: Point, marks: np.ndarray | None, settings: Settings, derived: Derived
) -> tuple[int, float]:
    """How many dashes run across the gap, and their period in wall thicknesses.

    A garage door is drawn as a long dashed rectangle spanning the opening. To
    a test that asks "does a line cross this gap?" that reads as a window on a
    bad day and as nothing at all on a good one, because the line keeps
    stopping. The break *is* the signal: counting how many times the ink starts
    again, and how regularly, separates a dashed line from a solid one and from
    a couple of stray marks.

    Measured per offset across the wall, as elsewhere, because the rectangle's
    two long sides sit either side of the gap's centre line. Returns (0, 0) when
    nothing along the gap looks dashed.
    """
    if marks is None:
        return (0, 0.0)
    length = math.dist(p1, p2)
    if length < 4:
        return (0, 0.0)

    ux = (p2[0] - p1[0]) / length
    uy = (p2[1] - p1[1]) / length
    nx, ny = -uy, ux

    steps = max(40, int(length * settings.garage_samples_per_px))
    half = max(1.0, derived.wall_thickness_px * settings.garage_band_ratio)

    # Every offset's every sample in one pass. This is called for each pair of
    # wall ends that could face each other, so a Python-level loop over the
    # samples costs more than the rest of the stage put together.
    along = np.linspace(0.0, length, steps + 1)
    offsets = np.linspace(-half, half, settings.garage_offset_samples)[:, None]
    xs = p1[0] + ux * along + nx * offsets
    ys = p1[1] + uy * along + ny * offsets

    height, width = marks.shape[:2]
    rows = np.rint(ys).astype(np.intp)
    cols = np.rint(xs).astype(np.intp)
    inside = (rows >= 0) & (rows < height) & (cols >= 0) & (cols < width)
    hits = np.zeros(rows.shape, dtype=bool)
    hits[inside] = marks[rows[inside], cols[inside]] > 0

    any_ink = hits.any(axis=1)
    if not any_ink.any():
        return (0, 0.0)

    # From the first mark to the last: dashes bunched at one end are some other
    # symbol poking into the opening, not a door filling it.
    first = hits.argmax(axis=1)
    last = hits.shape[1] - 1 - hits[:, ::-1].argmax(axis=1)
    coverage = hits.mean(axis=1)
    span = (last - first) / steps
    # A dash is a rising edge: paper, then ink.
    dashes = np.count_nonzero(hits[:, 1:] & ~hits[:, :-1], axis=1) + hits[:, 0]

    eligible = (
        any_ink
        & (coverage >= settings.garage_min_coverage)
        & (coverage <= settings.garage_max_coverage)
        & (span >= settings.garage_min_span)
    )
    if not eligible.any():
        return (0, 0.0)

    dashes = np.where(eligible, dashes, 0)
    pick = int(dashes.argmax())
    if dashes[pick] == 0:
        return (0, 0.0)
    period = span[pick] * length / dashes[pick] / derived.wall_thickness_px
    return (int(dashes[pick]), float(period))


def _is_garage(
    p1: Point, p2: Point, marks: np.ndarray | None, settings: Settings, derived: Derived
) -> bool:
    """Whether the gap is spanned by a dashed line at a believable period."""
    dashes, period = _dash_count(p1, p2, marks, settings, derived)
    return dashes >= settings.garage_min_dashes and period <= settings.garage_max_period_ratio


def classify_gap(
    gap: _Gap,
    ink: np.ndarray,
    settings: Settings,
    derived: Derived,
    marks: np.ndarray | None = None,
) -> tuple[OpeningKind, float, _DoorSwing | None]:
    """Decide whether a gap is a window, a door, or a plain opening.

    The door symbol is tested first because it is by far the more specific
    shape. Ink lying flat in the wall band could be a window reveal or a door
    leaf that happens to lie along the wall; a leaf-and-arc cannot be anything
    else.

    Confidence stays modest by design. Being sure a gap is *an opening* is what
    heals the wall and fixes the room outline, and that has already been decided
    by the time this runs; which kind of opening it is, is a further guess, and
    a wrong guess should not be presented as a certainty.
    """
    swing = _door_evidence(gap, ink, settings, derived)

    # A leaf this clear settles it: a window has no panel standing at a right
    # angle to the wall, so nothing else makes that mark.
    if swing is not None and swing.leaf >= settings.door_decisive_leaf:
        return ("door", float(np.clip(swing.score, 0.4, 0.9)), swing)

    # A dashed line spanning the opening is a garage door, and this is asked
    # before either line test because both of those read the dashes as evidence
    # of the wrong thing: enough of them in a row passes for a window, and the
    # rest of the time the breaks sink it to a plain opening. Counting dashes is
    # the more specific question, so it goes first.
    if _is_garage(gap.p1, gap.p2, marks, settings, derived):
        return ("garage", 0.6, None)

    # Otherwise the window test gets first refusal, because an arc on its own
    # is not as safe as it looks: basins, baths and toilet pans are curved and
    # sit hard against the very walls that windows are in, and they will fake
    # a swing. A line spanning the gap is the better evidence when both fit.
    run = _line_continues(gap.p1, gap.p2, marks, settings, derived)

    # A sliding door and a window both fill their opening with lines; what
    # separates them is whether it takes one line or two. This has to be asked
    # before the window question rather than inside it — two panels that only
    # just overlap never produce a single run long enough to look like a
    # window, and would otherwise fall through as a plain opening.
    #
    # Only when there is no door evidence at all: a leaf and an arc are the
    # more specific shape, and should not be talked out of.
    if swing is None and _sliding_span(gap, marks, settings, derived, run) > 0:
        span = _sliding_span(gap, marks, settings, derived, run)
        # Deliberately low. This reads a two-panel symbol from a handful of
        # sampled rays, and it has not been validated against enough plans to
        # be presented as anything better than a suggestion to check.
        return ("sliding", float(np.clip(0.35 + 0.2 * span, 0.35, 0.55)), None)

    if run >= settings.window_min_run:
        return ("window", float(np.clip(run * 0.85, 0.4, 0.85)), None)

    if swing is not None:
        return ("door", float(np.clip(swing.score, 0.4, 0.9)), swing)

    return ("opening", 0.5, None)


def _sample(ink: np.ndarray, x: float, y: float, slack: int = 0) -> bool:
    """Is there a mark at (x, y), give or take ``slack`` pixels?

    A little slack matters: the arc is one pixel wide and the sample points are
    on a circle, so an exact test misses it more often than it finds it.
    """
    height, width = ink.shape[:2]
    row, col = int(round(y)), int(round(x))
    for dr in range(-slack, slack + 1):
        for dc in range(-slack, slack + 1):
            r, c = row + dr, col + dc
            if 0 <= r < height and 0 <= c < width and ink[r, c]:
                return True
    return False


def _door_evidence(
    gap: _Gap, ink: np.ndarray, settings: Settings, derived: Derived
) -> _DoorSwing | None:
    """Look for the door symbol, whole, and report how it is hung.

    A door in plan is three things drawn together, and it is the combination
    that identifies it:

        wall ──┐ jamb
               │  leaf, at a right angle, one door-width long
               │
               ╰──╮  quarter arc, same radius
                   ╲
        wall ───────╯ back onto the opposite jamb

    Both parts are required. The arc alone is ambiguous — a curved furniture
    symbol or a second door's swing nearby will produce one — and the leaf alone
    is just a line. Requiring the leaf *and* an arc that closes from its tip
    back to the far jamb is a shape almost nothing else on a floor plan makes.

    All four ways a door can be hung are tried (either jamb, either side) and
    the best-scoring one is returned, so the reconstruction can draw the door
    the way the plan drew it rather than guessing a side.
    """
    radius = gap.width
    if radius < max(4.0, derived.wall_thickness_px):
        return None

    length = math.dist(gap.p1, gap.p2)
    if length <= 0:
        return None
    ux = (gap.p2[0] - gap.p1[0]) / length
    uy = (gap.p2[1] - gap.p1[1]) / length
    thickness = derived.wall_thickness_px
    slack = 1 if thickness < 8 else 2

    best: _DoorSwing | None = None
    for hinge_at_p1 in (True, False):
        hinge = gap.p1 if hinge_at_p1 else gap.p2
        # Unit vector from the hinge towards the far jamb, where the arc lands.
        towards = (ux, uy) if hinge_at_p1 else (-ux, -uy)
        for sign in (1.0, -1.0):
            leaf_dir = (-towards[1] * sign, towards[0] * sign)
            arc = _arc_coverage(ink, hinge, leaf_dir, towards, radius, settings, slack, thickness)
            if arc < settings.door_min_arc:
                continue
            leaf = _leaf_coverage(
                ink, hinge, leaf_dir, radius, settings, slack, thickness
            )
            # Rank by the arc, with the leaf as a tie-breaker. Ranking by the
            # weaker half instead lets a hanging with a coincidental leaf beat
            # the one with the real swing — which is how doors end up drawn
            # opening the wrong way.
            score = arc * (0.8 + 0.2 * leaf)
            if best is None or score > best.score:
                best = _DoorSwing(
                    score=score,
                    leaf=leaf,
                    arc=arc,
                    hinge_at_p1=hinge_at_p1,
                    swing_sign=sign,
                )
    return best


def _leaf_coverage(
    ink: np.ndarray,
    hinge: Point,
    direction: Point,
    length: float,
    settings: Settings,
    slack: int,
    thickness: float,
) -> float:
    """Fraction of the door leaf that is drawn.

    The hinge we have is the jamb on the wall's *centre line*, but the leaf is
    drawn pivoting at the wall *face*, and the detected jamb is itself a few
    pixels approximate. A single ray from the nominal hinge therefore misses a
    leaf that is plainly there, so a small fan of angles and lateral offsets is
    tried and the best taken.

    Sampling starts a little way out from the hinge: right at the jamb the wall
    itself is inked, and counting that would score every opening as a door.
    """
    samples = max(6, settings.door_leaf_samples)
    base = math.atan2(direction[1], direction[0])
    spread = math.radians(settings.door_leaf_angle_tol_deg)
    offsets = (0.0, thickness * 0.5, -thickness * 0.5)

    best = 0.0
    for angle in (base - spread, base, base + spread):
        dx, dy = math.cos(angle), math.sin(angle)
        for offset in offsets:
            # Shift the ray's origin along the wall, towards the far jamb.
            ox, oy = hinge[0] - dy * offset, hinge[1] + dx * offset
            hits = 0
            for i in range(samples):
                t = length * (
                    settings.door_leaf_skip
                    + (1.0 - settings.door_leaf_skip) * i / (samples - 1)
                )
                if _sample(ink, ox + dx * t, oy + dy * t, slack):
                    hits += 1
            best = max(best, hits / samples)
    return best


def _arc_coverage(
    ink: np.ndarray,
    hinge: Point,
    leaf_dir: Point,
    towards: Point,
    radius: float,
    settings: Settings,
    slack: int,
    thickness: float,
) -> float:
    """Fraction of the quarter arc, from the leaf's tip back to the far jamb.

    The pivot is searched over a small neighbourhood for the same reason the
    leaf is: the true hinge sits at a wall face, not on the centre line we
    measured, and the drawn radius is the leaf length rather than exactly the
    gap we found.
    """
    start = math.atan2(leaf_dir[1], leaf_dir[0])
    end = math.atan2(towards[1], towards[0])
    # Sweep the short way round: the arc is a quarter circle, not three.
    delta = (end - start + math.pi) % (2 * math.pi) - math.pi

    samples = max(6, settings.door_arc_samples)
    nudge = thickness * 0.5
    centres = (
        (hinge[0], hinge[1]),
        (hinge[0] - leaf_dir[0] * nudge, hinge[1] - leaf_dir[1] * nudge),
        (hinge[0] + towards[0] * nudge, hinge[1] + towards[1] * nudge),
    )

    best = 0.0
    for cx, cy in centres:
        hits = 0
        for i in range(samples):
            angle = start + delta * (i / (samples - 1))
            found = False
            for scale in (0.82, 0.9, 0.96, 1.02, 1.1):
                x = cx + math.cos(angle) * radius * scale
                y = cy + math.sin(angle) * radius * scale
                if _sample(ink, x, y, slack):
                    found = True
                    break
            hits += int(found)
        best = max(best, hits / samples)
    return best


# ---------------------------------------------------------------------------
# Healing the walls
# ---------------------------------------------------------------------------


def _merge_across(
    segments: list[Segment], gaps: list[_Gap]
) -> tuple[list[Segment], dict[int, int]]:
    """Fuse walls joined by a collinear opening into continuous runs.

    Returns the new wall list and a map from old segment index to new.
    """
    parent = list(range(len(segments)))

    def find(i: int) -> int:
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for gap in gaps:
        ra, rb = find(gap.a), find(gap.b)
        if ra != rb:
            parent[rb] = ra

    groups: dict[int, list[int]] = {}
    for index in range(len(segments)):
        groups.setdefault(find(index), []).append(index)

    walls: list[Segment] = []
    index_map: dict[int, int] = {}
    for members in groups.values():
        new_index = len(walls)
        walls.append(
            segments[members[0]] if len(members) == 1 else _fuse([segments[i] for i in members])
        )
        for old in members:
            index_map[old] = new_index
    return walls, index_map
