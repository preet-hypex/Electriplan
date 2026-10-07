"""Step 5: find the enclosed regions bounded by the reconstructed walls.

Rooms come from the *model*, not the pixels: the cleaned wall set is drawn back
onto a blank raster and the enclosed areas of that raster become rooms. That
way what the user sees on screen is what was measured, and editing a wall in
the editor would move the same boundary.

Doorways are the complication. A doorway is a real gap in a real wall, so the
raw wall raster leaks every bedroom into the hall. Gaps that look like openings
are therefore bridged with *virtual* segments that exist only in this raster —
they never enter the FloorPlan. How much of a room's boundary rests on virtual
rather than real wall is what its confidence score measures.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

import cv2
import numpy as np

from ..config import Derived, Settings
from .geometry import Segment

Point = tuple[float, float]


@dataclass
class RoomCandidate:
    polygon: list[Point]
    area: float
    centroid: Point
    label_point: Point
    confidence: float


@dataclass
class RoomDetection:
    rooms: list[RoomCandidate] = field(default_factory=list)
    #: Segments invented to bridge openings. Diagnostics only.
    virtual_segments: list[Segment] = field(default_factory=list)
    #: Enclosed regions rejected for being too small or too large.
    rejected: int = 0


def detect_rooms(
    segments: list[Segment],
    settings: Settings,
    derived: Derived,
    closures: list[Segment] | None = None,
) -> RoomDetection:
    """Find rooms enclosed by ``segments`` within a ``derived``-sized canvas.

    ``closures`` are gap-bridging segments the caller already worked out — the
    openings stage produces them as a by-product of classifying the gaps. When
    omitted they are computed here, which is what the unit tests do.
    """
    if not segments:
        return RoomDetection()

    virtual = bridge_openings(segments, settings, derived) if closures is None else closures

    real_raster = _rasterise(segments, derived)
    virtual_raster = _rasterise(virtual, derived)
    barrier = cv2.bitwise_or(real_raster, virtual_raster)

    interior = cv2.bitwise_not(barrier)
    count, labels, stats, centroids = cv2.connectedComponentsWithStats(interior, 4)

    min_area = settings.min_room_area_frac * derived.area
    max_area = settings.max_room_area_frac * derived.area

    detection = RoomDetection(virtual_segments=virtual)

    for index in range(1, count):
        x, y, w, h, area = (int(v) for v in stats[index, :5])
        # A region touching the canvas edge is the outside world, not a room.
        if x == 0 or y == 0 or x + w >= derived.width or y + h >= derived.height:
            continue
        if area < min_area or area > max_area:
            detection.rejected += 1
            continue

        mask = (labels == index).astype(np.uint8) * 255
        polygon = _region_polygon(mask, settings, derived)
        if polygon is None or len(polygon) < 3:
            detection.rejected += 1
            continue

        confidence = _boundary_confidence(mask, real_raster, virtual_raster)
        cx, cy = centroids[index]
        detection.rooms.append(
            RoomCandidate(
                polygon=polygon,
                area=float(area),
                centroid=(float(cx), float(cy)),
                label_point=_interior_point(mask, (float(cx), float(cy))),
                confidence=confidence,
            )
        )

    detection.rooms.sort(key=lambda r: -r.area)
    return detection


# ---------------------------------------------------------------------------
# Bridging openings
# ---------------------------------------------------------------------------


def bridge_openings(
    segments: list[Segment], settings: Settings, derived: Derived
) -> list[Segment]:
    """Segments that close the gaps in a wall set, for room finding only.

    The gap finding itself lives in :mod:`openings`, which also classifies each
    gap and heals the wall across it. By the time rooms are detected the door
    and window gaps are usually already gone — what is left here are the
    corner-to-corner gaps where a plan simply leaves the boundary open, plus
    any opening the caller chose not to heal.
    """
    from .openings import find_gaps

    return [
        Segment(
            x1=gap.p1[0],
            y1=gap.p1[1],
            x2=gap.p2[0],
            y2=gap.p2[1],
            thickness=max(segments[gap.a].thickness, segments[gap.b].thickness),
            confidence=0.0,
            fill=0.0,
        )
        for gap in find_gaps(segments, settings, derived)
    ]


def _rasterise(segments: list[Segment], derived: Derived) -> np.ndarray:
    canvas = np.zeros((derived.height, derived.width), dtype=np.uint8)
    for seg in segments:
        thickness = max(1, int(round(seg.thickness)))
        cv2.line(
            canvas,
            (int(round(seg.x1)), int(round(seg.y1))),
            (int(round(seg.x2)), int(round(seg.y2))),
            255,
            thickness,
            lineType=cv2.LINE_8,
        )
    return canvas


# ---------------------------------------------------------------------------
# Region to polygon
# ---------------------------------------------------------------------------


def _region_polygon(mask: np.ndarray, settings: Settings, derived: Derived) -> list[Point] | None:
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None
    contour = max(contours, key=cv2.contourArea)
    epsilon = max(1.0, settings.simplify_ratio * derived.wall_thickness_px)
    approx = cv2.approxPolyDP(contour, epsilon, True)
    points = [(float(p[0][0]), float(p[0][1])) for p in approx]
    snap = settings.polygon_axis_snap_ratio * derived.wall_thickness_px
    return rectilinear_simplify(points, snap)


def rectilinear_simplify(points: list[Point], tolerance: float) -> list[Point]:
    """Square up a traced outline and drop vertices that say nothing.

    Contour tracing produces staircases along what is really one straight edge.
    Each near-axis edge is forced flat, then redundant vertices are removed.
    """
    if len(points) < 3:
        return points

    result = [list(p) for p in points]
    for _ in range(2):
        count = len(result)
        for i in range(count):
            a = result[i]
            b = result[(i + 1) % count]
            dx = abs(b[0] - a[0])
            dy = abs(b[1] - a[1])
            if dx <= tolerance and dy > tolerance:
                mid = (a[0] + b[0]) / 2.0
                a[0] = b[0] = mid
            elif dy <= tolerance and dx > tolerance:
                mid = (a[1] + b[1]) / 2.0
                a[1] = b[1] = mid

    cleaned: list[Point] = []
    for point in result:
        candidate = (round(point[0], 2), round(point[1], 2))
        if cleaned and math.dist(cleaned[-1], candidate) <= max(1.0, tolerance / 2):
            continue
        cleaned.append(candidate)
    if len(cleaned) > 2 and math.dist(cleaned[0], cleaned[-1]) <= max(1.0, tolerance / 2):
        cleaned.pop()

    return _drop_collinear(cleaned, tolerance)


def _drop_collinear(points: list[Point], tolerance: float) -> list[Point]:
    if len(points) < 4:
        return points
    kept: list[Point] = []
    count = len(points)
    for i in range(count):
        prev = points[(i - 1) % count]
        here = points[i]
        nxt = points[(i + 1) % count]
        cross = (here[0] - prev[0]) * (nxt[1] - prev[1]) - (here[1] - prev[1]) * (nxt[0] - prev[0])
        base = math.dist(prev, nxt)
        if base > 0 and abs(cross) / base <= tolerance / 2:
            continue  # `here` sits on the line prev→next
        kept.append(here)
    return kept if len(kept) >= 3 else points


def _boundary_confidence(
    mask: np.ndarray, real_raster: np.ndarray, virtual_raster: np.ndarray
) -> float:
    """Score a room by how much of its perimeter is real wall.

    A bedroom ringed by detected walls scores high. A region that only closed
    because an opening was bridged scores low, and shows up highlighted in the
    editor for the user to check.
    """
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
    ring = cv2.subtract(cv2.dilate(mask, kernel), mask)
    real = int(np.count_nonzero(cv2.bitwise_and(ring, real_raster)))
    virtual = int(np.count_nonzero(cv2.bitwise_and(ring, virtual_raster)))
    total = real + virtual
    if total == 0:
        return 0.5
    return float(np.clip(0.55 + 0.44 * (real / total), 0.0, 0.99))


def _interior_point(mask: np.ndarray, centroid: Point) -> Point:
    """A point guaranteed to be inside the region — the centroid if possible.

    An L-shaped room's area centroid can fall in the notch, which would park
    the label outside the room.
    """
    cx, cy = centroid
    row, col = int(round(cy)), int(round(cx))
    height, width = mask.shape
    if 0 <= row < height and 0 <= col < width and mask[row, col]:
        return (cx, cy)

    # Widest interior run on the centroid's row, else the largest inscribed
    # circle's centre.
    if 0 <= row < height:
        xs = np.flatnonzero(mask[row])
        if xs.size:
            splits = np.split(xs, np.flatnonzero(np.diff(xs) > 1) + 1)
            widest = max(splits, key=len)
            return (float((widest[0] + widest[-1]) / 2.0), float(cy))

    dist = cv2.distanceTransform(mask, cv2.DIST_L2, 5)
    _, _, _, max_loc = cv2.minMaxLoc(dist)
    return (float(max_loc[0]), float(max_loc[1]))
