"""Step 4: turn wall pixels into wall objects.

The approach, in one line: *keep only strokes thick enough to be a wall, then
keep only runs long enough to be a wall, then measure each run's centre line
and thickness.*

Why not Hough lines? Hough finds the two edges of a wall as two separate lines
and says nothing about thickness, so every wall then has to be re-paired by
guesswork. Working from the filled stroke instead gives the centre line and the
thickness together, which is exactly the architectural object we want:

    two parallel black lines  ->  one filled band  ->  centre line + thickness

Canny/Hough is kept as a fallback for line-art plans whose walls are drawn as
outlines rather than solid fills.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import cv2
import numpy as np

from ..config import Derived, Settings
from .geometry import Segment


@dataclass
class WallDetection:
    segments: list[Segment]
    #: Mask of strokes thick enough to be walls (diagnostics and room fallback).
    thick_mask: np.ndarray
    #: Thick ink that no horizontal or vertical wall explains.
    residual_fraction: float
    used_fallback: bool
    #: Thick bars too short to be walls on their own. A doorway leaves a short
    #: return of wall at each jamb, and those returns are what mark where the
    #: opening ends — so they are kept aside rather than discarded, and later
    #: re-admitted if they line up with a real wall. See geometry.readmit_stubs.
    stubs: list[Segment] = field(default_factory=list)


def detect_walls(ink: np.ndarray, settings: Settings, derived: Derived) -> WallDetection:
    """Detect walls in a binary ink mask of the plan region."""
    thick = _thick_strokes(ink, derived.open_kernel_px)

    horizontal = _directional_open(thick, derived.min_wall_length_px, axis="h")
    vertical = _directional_open(thick, derived.min_wall_length_px, axis="v")

    segments: list[Segment] = []
    segments += _bars_to_segments(horizontal, settings, derived, axis="h")
    segments += _bars_to_segments(vertical, settings, derived, axis="v")

    residual = _residual_fraction(thick, horizontal, vertical, derived)

    # A second, much shorter pass to catch the jamb returns.
    stub_min = max(3, int(round(settings.stub_min_thickness_ratio * derived.wall_thickness_px)))
    stubs: list[Segment] = []
    if stub_min < derived.min_wall_length_px:
        for axis in ("h", "v"):
            bars = _directional_open(thick, stub_min, axis=axis)
            stubs += _bars_to_segments(bars, settings, derived, axis=axis, min_run=stub_min)
        stubs = [s for s in stubs if s.length < derived.min_wall_length_px]

    used_fallback = False
    if len(segments) < settings.min_expected_walls:
        # Outline-drawn plans have no thick strokes to open; fall back to edge
        # detection so we return something rather than nothing.
        fallback = _hough_fallback(ink, settings, derived)
        if len(fallback) > len(segments):
            segments = fallback
            used_fallback = True

    return WallDetection(
        segments=segments,
        stubs=[] if used_fallback else stubs,
        thick_mask=thick,
        residual_fraction=residual,
        used_fallback=used_fallback,
    )


def _thick_strokes(ink: np.ndarray, kernel_px: int) -> np.ndarray:
    """Keep only ink that survives an opening — i.e. strokes at least this thick.

    This is the single most valuable step in the pipeline: it deletes text,
    dimension lines, door arcs, hatching and furniture in one operation,
    because none of them are drawn as thick as a wall.
    """
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_px, kernel_px))
    return cv2.morphologyEx(ink, cv2.MORPH_OPEN, kernel)


def _directional_open(mask: np.ndarray, min_length: int, axis: str) -> np.ndarray:
    """Keep runs at least ``min_length`` long in one direction."""
    size = (min_length, 1) if axis == "h" else (1, min_length)
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, size)
    return cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)


def _bars_to_segments(
    mask: np.ndarray,
    settings: Settings,
    derived: Derived,
    axis: str,
    min_run: int | None = None,
) -> list[Segment]:
    """Trace each bar in a directional mask into a centre line plus thickness.

    A connected component is scanned column by column (row by row for vertical
    walls). Each column contributes one or more runs; runs whose centres line
    up from column to column belong to the same bar. Tracking centres rather
    than taking the component's bounding box keeps two walls that meet at a
    thick junction from being reported as one fat diagonal.
    """
    length_floor = derived.min_wall_length_px if min_run is None else min_run

    if axis == "v":
        mask = mask.T

    count, labels, stats, _ = cv2.connectedComponentsWithStats(mask, 8)
    segments: list[Segment] = []

    for index in range(1, count):
        x, y, w, h, _area = (int(v) for v in stats[index, :5])
        if w < length_floor:
            continue
        component = labels[y : y + h, x : x + w] == index
        for track in _trace_tracks(component, settings, derived, length_floor):
            seg = _track_to_segment(track, offset=(x, y), axis=axis, settings=settings)
            if seg is not None and seg.length >= length_floor:
                segments.append(seg)

    return segments


@dataclass
class _Track:
    """A bar being followed across columns."""

    start_col: int
    last_col: int
    centres: list[float]
    thicknesses: list[float]
    columns: list[int]
    ink_pixels: int

    @property
    def span(self) -> int:
        return self.last_col - self.start_col + 1


def _trace_tracks(
    component: np.ndarray, settings: Settings, derived: Derived, min_span: int | None = None
) -> list[_Track]:
    """Follow the bars inside one connected component."""
    height, width = component.shape
    open_tracks: list[_Track] = []
    finished: list[_Track] = []
    centre_tol = max(1.5, settings.track_centre_tol_ratio * derived.wall_thickness_px)

    for col in range(width):
        column = component[:, col]
        runs = _runs(column)

        unmatched = list(open_tracks)
        for start, end in runs:
            centre = (start + end) / 2.0
            thickness = float(end - start + 1)
            # Match to the closest still-open track whose centre is near enough.
            best: _Track | None = None
            best_delta = centre_tol
            for track in unmatched:
                delta = abs(track.centres[-1] - centre)
                if delta <= best_delta:
                    best, best_delta = track, delta
            if best is None:
                best = _Track(col, col, [], [], [], 0)
                open_tracks.append(best)
            else:
                unmatched.remove(best)
            best.last_col = col
            best.centres.append(centre)
            best.thicknesses.append(thickness)
            best.columns.append(col)
            best.ink_pixels += int(thickness)

        # Close tracks that have gone quiet for longer than the gap tolerance.
        still_open: list[_Track] = []
        for track in open_tracks:
            if col - track.last_col > settings.track_gap_px:
                finished.append(track)
            else:
                still_open.append(track)
        open_tracks = still_open

    finished.extend(open_tracks)
    del height  # only needed for clarity above
    floor = derived.min_wall_length_px if min_span is None else min_span
    return [t for t in finished if t.span >= floor]


def _runs(column: np.ndarray) -> list[tuple[int, int]]:
    """Inclusive [start, end] index pairs of True runs in a boolean column."""
    if not column.any():
        return []
    padded = np.concatenate(([False], column, [False]))
    edges = np.flatnonzero(padded[1:] != padded[:-1])
    return [(int(edges[i]), int(edges[i + 1]) - 1) for i in range(0, len(edges), 2)]


def _track_to_segment(
    track: _Track, offset: tuple[int, int], axis: str, settings: Settings
) -> Segment | None:
    """Fit a centre line through a track and score how solid it was."""
    if len(track.columns) < 2:
        return None

    columns = np.asarray(track.columns, dtype=np.float64)
    centres = np.asarray(track.centres, dtype=np.float64)

    # A straight-line fit tolerates a slightly rotated scan; for a truly
    # axis-aligned wall it reduces to the median centre.
    slope, intercept = np.polyfit(columns, centres, 1)
    c0, c1 = float(columns[0]), float(columns[-1])
    a0, a1 = slope * c0 + intercept, slope * c1 + intercept

    thickness = float(np.median(track.thicknesses))
    span = c1 - c0 + 1.0
    fill = float(np.clip(track.ink_pixels / max(1.0, span * thickness), 0.0, 1.0))
    if fill < settings.min_fill_ratio:
        return None

    ox, oy = offset
    if axis == "h":
        p1 = (c0 + ox, a0 + oy)
        p2 = (c1 + ox, a1 + oy)
    else:
        # The mask was transposed for vertical tracing; swap back. The offset
        # is in transposed coordinates too, so it swaps with the points.
        p1 = (a0 + oy, c0 + ox)
        p2 = (a1 + oy, c1 + ox)

    # Solid, long bars are the ones we trust. Fill dominates because a partly
    # empty bar usually means the tracer walked across a hatched area.
    confidence = float(np.clip(0.45 + 0.5 * fill, 0.0, 0.99))

    return Segment(
        x1=p1[0], y1=p1[1], x2=p2[0], y2=p2[1], thickness=thickness, confidence=confidence, fill=fill
    )


def _residual_fraction(
    thick: np.ndarray, horizontal: np.ndarray, vertical: np.ndarray, derived: Derived
) -> float:
    """How much thick ink neither axis explains — a proxy for diagonal walls."""
    explained = cv2.bitwise_or(horizontal, vertical)
    kernel = cv2.getStructuringElement(
        cv2.MORPH_ELLIPSE, (derived.open_kernel_px, derived.open_kernel_px)
    )
    explained = cv2.dilate(explained, kernel)
    residual = cv2.bitwise_and(thick, cv2.bitwise_not(explained))
    total = float(np.count_nonzero(thick))
    if total == 0:
        return 0.0
    return float(np.count_nonzero(residual) / total)


def _hough_fallback(ink: np.ndarray, settings: Settings, derived: Derived) -> list[Segment]:
    """Edge-and-Hough detection for plans whose walls are outlines, not fills.

    Produces lower-confidence segments with an assumed thickness, because an
    outline gives no thickness to measure.
    """
    edges = cv2.Canny(ink, 50, 150, apertureSize=3)
    lines = cv2.HoughLinesP(
        edges,
        rho=1,
        theta=np.pi / 180.0,
        threshold=max(30, derived.min_wall_length_px),
        minLineLength=derived.min_wall_length_px,
        maxLineGap=max(3, derived.open_kernel_px),
    )
    if lines is None or lines.size == 0:
        return []

    # OpenCV has returned both (N, 1, 4) and (N, 4) across versions.
    coordinates = lines.reshape(-1, 4)

    segments: list[Segment] = []
    for line in coordinates:
        x1, y1, x2, y2 = (float(v) for v in line)
        seg = Segment(
            x1=x1,
            y1=y1,
            x2=x2,
            y2=y2,
            thickness=derived.wall_thickness_px,
            confidence=0.4,
            fill=1.0,
        )
        angle = seg.angle_deg
        near_axis = min(angle, 180.0 - angle) <= settings.axis_snap_deg or (
            abs(angle - 90.0) <= settings.axis_snap_deg
        )
        if near_axis:
            segments.append(seg)
    return segments
