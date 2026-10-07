"""Runs the whole pipeline and assembles the FloorPlan.

    image -> normalise -> region -> walls -> cleanup -> rooms
                                      \\-> OCR -> scale -> millimetres

OCR runs on the same region as wall detection but sees none of its output; the
two only meet here, where labels are matched to rooms and the scale is derived
from the pair. Because they are independent they are run at the same time —
Tesseract is a separate process, so the branches genuinely overlap rather than
taking turns.
"""

from __future__ import annotations

import math
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, replace

import numpy as np

from .. import models
from ..config import SECONDARY_ROOM_NAMES, Derived, PipelineConfig, Settings, derive
from . import ocr as ocr_module
from . import openings as openings_module
from . import preprocess, region as region_module, rooms as rooms_module, scale as scale_module
from . import walls as walls_module
from .geometry import (
    Segment,
    building_bounds,
    clean,
    drop_detached_furniture,
    find_intersections,
    readmit_stubs,
)

Point = tuple[float, float]


@dataclass
class AnalysisResult:
    plan: models.FloorPlan
    #: Pixel-space artefacts, kept for tests and future debugging endpoints.
    segments: list[Segment]
    region: region_module.Region
    derived: Derived


class AnalysisError(RuntimeError):
    """The pipeline could not produce a plan at all."""


def _read_page(
    gray_region: np.ndarray, settings: Settings, derived: Derived
) -> tuple[list[ocr_module.TextItem], bool, str, str | None]:
    """Read the page, reporting a missing Tesseract rather than raising.

    Runs on its own thread, so it returns the warning instead of appending to
    a shared list.
    """
    try:
        items = ocr_module.extract_text(gray_region, settings, derived.wall_thickness_px)
    except ocr_module.OcrUnavailable as exc:
        return ([], False, "unavailable", str(exc))
    return (items, True, f"{len(items)} text lines", None)


def analyse(
    image_bytes: bytes,
    image_url: str,
    config: PipelineConfig | None = None,
) -> AnalysisResult:
    """Reconstruct a FloorPlan from an uploaded floor-plan image."""
    config = config or PipelineConfig()
    settings = config.settings
    steps: list[models.AnalysisStep] = []
    warnings: list[str] = []

    # -- 1. load and normalise --------------------------------------------
    original = preprocess.load_image(image_bytes)
    normalised = preprocess.normalise(original, settings)
    steps.append(
        models.AnalysisStep(
            name="Image processed",
            ok=True,
            detail=f"{normalised.width}×{normalised.height} px",
        )
    )

    # -- 2. plan region ----------------------------------------------------
    region = region_module.detect_region(normalised.gray, settings)
    if region.fallback_reason:
        warnings.append(region.fallback_reason + " Crop the image and retry if walls are missed.")
    steps.append(
        models.AnalysisStep(
            name="Floor plan region detected",
            ok=region.fallback_reason is None,
            detail=f"{region.width}×{region.height} px at ({region.x}, {region.y})",
        )
    )

    gray_region = region.crop(normalised.gray)
    ink = preprocess.binarise(gray_region)
    # Thickness and minimum wall length are mutually dependent: the run length
    # used to isolate walls comes from the image size, and everything else then
    # comes from the thickness those runs turn out to have.
    min_run = derive(settings, region.width, region.height, 0.0).min_wall_length_px
    thickness = preprocess.estimate_wall_thickness(ink, settings, min_run)
    # ...and separately, the commonest wall width, which is what the opening
    # kernel is sized from. See preprocess.dominant_stroke_width.
    stroke = preprocess.dominant_stroke_width(ink, settings, min_run)
    derived = derive(settings, region.width, region.height, thickness, stroke)
    # A second, looser view of the page: whatever is drawn, however lightly.
    # Windows are frequently drawn in a lighter tone than walls.
    marks = preprocess.faint_marks(
        gray_region, settings, int(thickness * settings.faint_mark_block_ratio)
    )

    # -- text, read alongside the geometry ---------------------------------
    # Nothing in the geometry stages looks at the text and nothing in the text
    # stage looks at the geometry — they only meet at the end, when a name is
    # matched to a room. So the page is read while the walls are being found.
    # Tesseract is a separate process, so the wait really does overlap, and the
    # stage that used to be half the analysis is now mostly free.
    reader = ThreadPoolExecutor(max_workers=1)
    reading = reader.submit(_read_page, gray_region, settings, derived)

    try:
        # -- 3. walls ------------------------------------------------------
        detection = walls_module.detect_walls(ink, settings, derived)
        segments = clean(detection.segments, settings, derived.min_wall_length_px)
        # Jamb returns come back now, before openings are looked for: a doorway
        # needs both of its ends before it can be found as a gap at all.
        before_stubs = len(segments)
        segments = readmit_stubs(
            segments,
            detection.stubs,
            settings,
            derived.wall_thickness_px,
            settings.max_opening_frac * derived.min_dim,
        )

        if not segments:
            raise AnalysisError(
                "No walls could be detected. The image may not be a floor plan, or the "
                "walls may be drawn too faintly to separate from the text."
            )

        if detection.used_fallback:
            warnings.append(
                "No thick wall strokes were found, so edge detection was used instead. "
                "Wall thickness is a guess — check the result against the original."
            )
        if detection.residual_fraction > settings.diagonal_residual_warn_frac:
            warnings.append(
                f"{detection.residual_fraction:.0%} of the wall ink is neither horizontal nor "
                "vertical. This MVP reconstructs orthogonal walls only; any angled walls are "
                "missing and must be drawn by hand."
            )
        if len(segments) < settings.min_expected_walls:
            warnings.append(
                f"Only {len(segments)} walls were detected. Detection confidence is low — "
                "try again or trace the walls manually."
            )

        steps.append(
            models.AnalysisStep(
                name="Walls detected",
                ok=len(segments) >= settings.min_expected_walls,
                detail=(
                    f"{len(detection.segments)} raw → {before_stubs} after cleanup, "
                    f"+{len(segments) - before_stubs} jambs, "
                    f"{len(find_intersections(segments))} junctions"
                ),
            )
        )

        # -- 4. openings -------------------------------------------------------
        # Classify the gaps in the walls and heal the walls across them, so rooms
        # are bounded by whole walls rather than by fragments.
        resolved = openings_module.resolve_openings(
            segments, ink, settings, derived, marks
        )
        # The agency logo is drawn as heavily as a wall and reaches this point
        # as a small detached cluster of perfectly good ones. It has to be
        # dropped *after* healing, not before: until the doorways are fused the
        # building is not one connected run either, and a rule about what is
        # detached would throw away half the house.
        touch = settings.furniture_touch_ratio * derived.wall_thickness_px
        kept_walls, furniture = drop_detached_furniture(resolved.walls, touch)
        segments = kept_walls
        if furniture:
            # Openings are held as an index into the wall list, so dropping a
            # wall renumbers every wall after it.
            dropped = {id(w) for w in furniture}
            renumber = {}
            for old_index, wall in enumerate(resolved.walls):
                if id(wall) not in dropped:
                    renumber[old_index] = len(renumber)
            resolved.openings = [
                replace(o, wall_index=renumber[o.wall_index])
                for o in resolved.openings
                if o.wall_index in renumber
            ]
        if furniture:
            steps.append(
                models.AnalysisStep(
                    name="Page furniture removed",
                    ok=True,
                    detail=(
                        f"{len(furniture)} walls belonged to the agency logo or the "
                        "footer, not the building"
                    ),
                )
            )
        steps.append(
            models.AnalysisStep(
                name="Openings detected",
                ok=True,
                detail=(
                    f"{len(resolved.doors)} doors, {len(resolved.sliding)} sliding, "
                    f"{len(resolved.garages)} garage, {len(resolved.windows)} windows, "
                    f"{len(resolved.openings) - len(resolved.doors) - len(resolved.sliding) - len(resolved.garages) - len(resolved.windows)} "
                    "plain openings"
                ),
            )
        )

        # -- 5. rooms ----------------------------------------------------------
        room_detection = rooms_module.detect_rooms(
            segments, settings, derived, closures=resolved.closures
        )
        steps.append(
            models.AnalysisStep(
                name="Rooms detected",
                ok=bool(room_detection.rooms),
                detail=(
                    f"{len(room_detection.rooms)} enclosed regions, "
                    f"{len(room_detection.virtual_segments)} openings bridged"
                ),
            )
        )
        if not room_detection.rooms:
            warnings.append(
                "No enclosed rooms were found. The wall outline is probably broken — "
                "close the gaps in the editor and the rooms will follow."
            )

    finally:
        # The reader is joined either way: an exception in the geometry must
        # not leave a Tesseract process running behind it.
        text_items, ocr_ok, ocr_detail, ocr_warning = reading.result()
        reader.shutdown()
    if ocr_warning:
        warnings.append(ocr_warning)

    # Judged against the building, not against every wall detected.
    text_items = _drop_page_furniture(
        text_items, building_bounds(segments, touch), settings
    )
    room_texts = [t for t in text_items if t.kind == "room"]
    dimension_texts = [t for t in text_items if t.kind == "dimension"]

    steps.append(
        models.AnalysisStep(
            name="Text detected",
            ok=ocr_ok,
            detail=ocr_detail if not ocr_ok else f"{len(room_texts)} room names",
        )
    )
    steps.append(
        models.AnalysisStep(
            name="Dimensions detected",
            ok=bool(dimension_texts),
            detail=f"{len(dimension_texts)} dimension strings",
        )
    )

    # -- 7. match labels to rooms -----------------------------------------
    max_label_distance = settings.label_room_max_dist_frac * derived.min_dim
    room_names = _assign_names(room_detection.rooms, room_texts, max_label_distance)
    measured = _measure_rooms(room_detection.rooms, dimension_texts, room_names, max_label_distance)

    # -- 8. scale ----------------------------------------------------------
    if config.mm_per_px_override is not None:
        estimate = scale_module.ScaleEstimate(
            mm_per_px=config.mm_per_px_override,
            confidence=1.0,
            method="manual",
            note="Scale supplied by the caller.",
        )
    else:
        estimate = scale_module.estimate_scale(
            scale_module.samples_from_rooms(measured, settings.scale_aspect_tol),
            settings,
            float(region.width),
            # The walls that were actually detected, not the pre-detection
            # estimate: their median is the internal partition thickness.
            wall_thickness_px=scale_module.median([s.thickness for s in segments]),
        )
    if estimate.note:
        warnings.append(estimate.note)

    steps.append(
        models.AnalysisStep(
            name="Scale estimated",
            ok=estimate.confidence >= 0.6,
            detail=f"{estimate.mm_per_px:.2f} mm/px ({estimate.confidence:.0%} confident)",
        )
    )

    # -- 9. build the model in millimetres ---------------------------------
    plan = _build_plan(
        segments=segments,
        openings=resolved.openings,
        rooms=room_detection.rooms,
        room_names=room_names,
        text_items=text_items,
        measured=measured,
        estimate=estimate,
        mm_per_px=estimate.mm_per_px,
    )

    plan.source = models.PlanSource(
        image_url=image_url,
        image_width=normalised.width,
        image_height=normalised.height,
        plan_region=models.Rect(
            x=region.x, y=region.y, width=region.width, height=region.height
        ),
        mm_per_px=estimate.mm_per_px,
        scale_confidence=estimate.confidence,
        scale_method=estimate.method,  # type: ignore[arg-type]
    )
    plan.analysis = models.AnalysisReport(
        steps=steps,
        wall_count=len(plan.walls),
        room_count=len(plan.rooms),
        label_count=len(plan.labels),
        dimension_count=len(plan.dimensions),
        warnings=warnings,
    )

    return AnalysisResult(plan=plan, segments=segments, region=region, derived=derived)


# ---------------------------------------------------------------------------
# Assembly helpers
# ---------------------------------------------------------------------------


def _segment_bounds(segments: list[Segment]) -> tuple[float, float, float, float]:
    xs = [v for s in segments for v in (s.x1, s.x2)]
    ys = [v for s in segments for v in (s.y1, s.y2)]
    return (min(xs), min(ys), max(xs), max(ys))


def _drop_page_furniture(
    items: list[ocr_module.TextItem],
    bounds: tuple[float, float, float, float],
    settings: Settings,
) -> list[ocr_module.TextItem]:
    """Discard the address line, the disclaimer, the logo and OCR gibberish.

    Two filters, both applied only to text that matched neither a room name nor
    a dimension — those are never page furniture, and a name printed just
    outside a wall should still find its room.

    * Position: anything outside the *building's* bounding box is on the page,
      not on the plan. The building rather than every wall detected, because
      an agency logo traced as walls would otherwise stretch the box down over
      the very disclaimer this is meant to catch.
    * Substance: one- and two-character fragments read out of furniture symbols
      and hatching are noise. Keeping them would put "xX" and "KD" on the
      canvas and inflate the label count the analysis screen reports.
    """
    min_x, min_y, max_x, max_y = bounds
    margin = 0.03 * max(max_x - min_x, max_y - min_y)

    kept: list[ocr_module.TextItem] = []
    for item in items:
        if item.kind != "other":
            kept.append(item)
            continue

        cx, cy = item.bbox.centre
        inside = min_x - margin <= cx <= max_x + margin and min_y - margin <= cy <= max_y + margin
        if not inside:
            continue

        alphanumeric = sum(1 for c in item.text if c.isalnum())
        if alphanumeric < settings.ocr_min_other_characters:
            continue
        if item.confidence < settings.ocr_min_other_confidence:
            continue
        kept.append(item)
    return kept


def _point_in_polygon(polygon: list[Point], point: Point) -> bool:
    inside = False
    count = len(polygon)
    for i in range(count):
        a = polygon[i]
        b = polygon[(i - 1) % count]
        if (a[1] > point[1]) != (b[1] > point[1]):
            x_at = (b[0] - a[0]) * (point[1] - a[1]) / (b[1] - a[1]) + a[0]
            if point[0] < x_at:
                inside = not inside
    return inside


def _room_for_point(
    candidates: list[rooms_module.RoomCandidate], point: Point, max_distance: float
) -> int | None:
    """The room containing ``point``, else the nearest one within range."""
    for index, room in enumerate(candidates):
        if _point_in_polygon(room.polygon, point):
            return index

    best_index, best_distance = None, max_distance
    for index, room in enumerate(candidates):
        distance = math.dist(room.centroid, point)
        if distance < best_distance:
            best_index, best_distance = index, distance
    return best_index


def _assign_names(
    candidates: list[rooms_module.RoomCandidate],
    room_texts: list[ocr_module.TextItem],
    max_distance: float,
) -> dict[int, ocr_module.TextItem]:
    """Pick one name per room.

    A robe, a linen cupboard or a shower is a thing inside a room rather than a
    room, so where one of those labels lands in the same region as a real room
    name the real name wins however confidently the other was read — otherwise
    a bedroom whose robe was not resolved as its own region comes back as BIR.

    Past that, confidence decides. An open-plan region can hold several equally
    confident names ("LIVING", "DINING", "ENTRY"); rounding the confidence lets
    those tie, and the tie goes to whichever name sits closest to the middle of
    the region — the one a person would say the space is.
    """
    per_room: dict[int, list[ocr_module.TextItem]] = {}
    for text in room_texts:
        index = _room_for_point(candidates, text.bbox.centre, max_distance)
        if index is not None:
            per_room.setdefault(index, []).append(text)

    assigned: dict[int, ocr_module.TextItem] = {}
    for index, texts in per_room.items():
        anchor = candidates[index].label_point
        assigned[index] = min(
            texts,
            key=lambda t: (
                t.room_name in SECONDARY_ROOM_NAMES,
                -round(t.confidence, 2),
                math.dist(t.bbox.centre, anchor),
            ),
        )
    return assigned


def _measure_rooms(
    candidates: list[rooms_module.RoomCandidate],
    dimension_texts: list[ocr_module.TextItem],
    room_names: dict[int, ocr_module.TextItem],
    max_distance: float,
) -> list[scale_module.MeasuredRoom]:
    """Pair each dimension string with the room it was written in."""
    measured: list[scale_module.MeasuredRoom] = []
    used: set[int] = set()
    for text in sorted(dimension_texts, key=lambda t: -t.confidence):
        if text.dimensions_mm is None:
            continue
        index = _room_for_point(candidates, text.bbox.centre, max_distance)
        if index is None or index in used:
            continue
        used.add(index)
        room = candidates[index]
        xs = [p[0] for p in room.polygon]
        ys = [p[1] for p in room.polygon]
        named = room_names.get(index)
        measured.append(
            scale_module.MeasuredRoom(
                width_px=max(xs) - min(xs),
                height_px=max(ys) - min(ys),
                dimensions_mm=text.dimensions_mm,
                name=named.room_name if named and named.room_name else "",
            )
        )
    return measured


def _build_plan(
    segments: list[Segment],
    openings: list[openings_module.Opening],
    rooms: list[rooms_module.RoomCandidate],
    room_names: dict[int, ocr_module.TextItem],
    text_items: list[ocr_module.TextItem],
    measured: list[scale_module.MeasuredRoom],
    estimate: scale_module.ScaleEstimate,
    mm_per_px: float,
) -> models.FloorPlan:
    """Convert pixel geometry to millimetres and populate the model."""
    del measured  # already folded into `estimate`

    def point(p: Point) -> models.Point:
        return models.Point(x=round(p[0] * mm_per_px, 1), y=round(p[1] * mm_per_px, 1))

    plan = models.FloorPlan()

    for index, seg in enumerate(segments, start=1):
        plan.walls.append(
            models.Wall(
                id=f"wall_{index:03d}",
                start=point(seg.p1),
                end=point(seg.p2),
                thickness=round(seg.thickness * mm_per_px, 1),
                confidence=round(seg.confidence, 3),
                source="vision",
            )
        )

    door_count = 0
    window_count = 0
    opening_count = 0
    for opening in openings:
        if not (0 <= opening.wall_index < len(plan.walls)):
            continue
        wall_id = plan.walls[opening.wall_index].id
        position = round(opening.position * mm_per_px, 1)
        width = round(opening.width * mm_per_px, 1)
        if opening.kind in ("door", "sliding", "garage"):
            door_count += 1
            plan.doors.append(
                models.Door(
                    id=f"door_{door_count:03d}",
                    wall_id=wall_id,
                    position=position,
                    width=width,
                    style="swing" if opening.kind == "door" else opening.kind,
                    hinge_at_start=opening.hinge_at_start,
                    swing=opening.swing_degrees,
                    confidence=round(opening.confidence, 3),
                    source="vision",
                )
            )
        elif opening.kind == "opening":
            opening_count += 1
            plan.openings.append(
                models.Opening(
                    id=f"opening_{opening_count:03d}",
                    wall_id=wall_id,
                    position=position,
                    width=width,
                    confidence=round(opening.confidence, 3),
                    source="vision",
                )
            )
        elif opening.kind == "window":
            window_count += 1
            plan.windows.append(
                models.Window(
                    id=f"window_{window_count:03d}",
                    wall_id=wall_id,
                    position=position,
                    width=width,
                    confidence=round(opening.confidence, 3),
                    source="vision",
                )
            )

    room_ids: dict[int, str] = {}
    for index, room in enumerate(rooms, start=1):
        room_id = f"room_{index:03d}"
        room_ids[index - 1] = room_id
        named = room_names.get(index - 1)
        # A room whose name was read is more certain than one that is only a
        # shape; a nameless one is flagged for the user to check.
        confidence = room.confidence
        if named is not None:
            confidence = min(0.99, (confidence + named.confidence) / 2.0 + 0.05)
        else:
            confidence *= 0.7
        plan.rooms.append(
            models.Room(
                id=room_id,
                name=named.room_name if named and named.room_name else "",
                polygon=[point(p) for p in room.polygon],
                label_position=point(room.label_point),
                confidence=round(confidence, 3),
                source="ocr" if named is not None else "geometry",
            )
        )

    # Every piece of recognised text becomes a label, with its bounding box
    # centre as the position. Room names additionally carry their room id.
    text_to_room = {id(text): index for index, text in room_names.items()}
    for index, item in enumerate(text_items, start=1):
        room_index = text_to_room.get(id(item))
        plan.labels.append(
            models.Label(
                id=f"label_{index:03d}",
                text=item.room_name if item.kind == "room" and item.room_name else item.text,
                position=point(item.bbox.centre),
                type=item.kind if item.kind in ("room", "dimension") else "other",  # type: ignore[arg-type]
                room_id=room_ids.get(room_index) if room_index is not None else None,
                confidence=round(item.confidence, 3),
                source="ocr",
            )
        )

    # Dimension strings become measurable objects drawn across the room they
    # describe, so the numbers can be checked against the geometry on screen.
    counter = 0
    for item in text_items:
        if item.kind != "dimension" or item.dimensions_mm is None:
            continue
        index = _room_for_point(rooms, item.bbox.centre, math.inf)
        if index is None:
            continue
        room = rooms[index]
        xs = [p[0] for p in room.polygon]
        ys = [p[1] for p in room.polygon]
        span_px = sorted(((max(xs) - min(xs), "h"), (max(ys) - min(ys), "v")))
        values = sorted(item.dimensions_mm)
        for (length_px, axis), value in zip(span_px, values):
            del length_px
            counter += 1
            if axis == "h":
                start = (min(xs), min(ys))
                end = (max(xs), min(ys))
            else:
                start = (min(xs), min(ys))
                end = (min(xs), max(ys))
            plan.dimensions.append(
                models.Dimension(
                    id=f"dim_{counter:03d}",
                    start=point(start),
                    end=point(end),
                    value=round(value, 1),
                    unit="mm",
                    confidence=round(item.confidence, 3),
                    source="ocr",
                )
            )

    del estimate
    return plan
