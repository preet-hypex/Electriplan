"""Step 7: work out how many millimetres one source pixel is worth.

Every dimension string the OCR found ("3.5m X 6.0m") is matched to the room it
sits in. That room's measured pixel size then gives two independent samples of
mm-per-pixel. The median across every sample is the estimate; how tightly the
samples agree is the confidence.

The median rather than the mean because one mismatched label — a dimension
read into the wrong room — would otherwise drag the whole plan out of scale.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from ..config import Settings

Point = tuple[float, float]


@dataclass
class ScaleSample:
    mm_per_px: float
    #: What produced it, for the analysis report.
    detail: str


@dataclass
class ScaleEstimate:
    mm_per_px: float
    confidence: float
    method: str  # "ocr-dimensions" | "manual" | "fallback"
    samples: list[ScaleSample] = field(default_factory=list)
    note: str = ""


@dataclass
class MeasuredRoom:
    """A room's pixel bounding box paired with the dimensions written in it."""

    width_px: float
    height_px: float
    dimensions_mm: tuple[float, float]
    name: str = ""


def aspect_ratio(a: float, b: float) -> float:
    """Long side over short side, so it does not matter which is which."""
    lo, hi = sorted((abs(a), abs(b)))
    return hi / lo if lo > 0 else float("inf")


def samples_from_rooms(
    measured: list[MeasuredRoom], aspect_tolerance: float
) -> list[ScaleSample]:
    """Turn matched rooms into mm-per-pixel samples.

    A dimension string does not say which value is the width, so both the pixel
    pair and the millimetre pair are sorted before being paired up. For a room
    that is 3.5 × 6.0 m this is unambiguous; for a square room it makes no
    difference.

    A room whose *shape* disagrees with its label is dropped rather than
    measured. "3.6m X 4.0m" is nearly square; if the region it landed in is
    twice as long as it is wide, that region is not the room the label names —
    almost always because an open-plan area swallowed it — and measuring it
    would put the whole plan out of scale by the same factor.
    """
    samples: list[ScaleSample] = []
    for room in measured:
        px = sorted((room.width_px, room.height_px))
        mm = sorted(room.dimensions_mm)
        if px[0] <= 1.0:
            continue

        shape_error = aspect_ratio(px[1], px[0]) / aspect_ratio(mm[1], mm[0])
        if not (1.0 / (1.0 + aspect_tolerance) <= shape_error <= 1.0 + aspect_tolerance):
            continue

        for pixels, millimetres in zip(px, mm):
            samples.append(
                ScaleSample(
                    mm_per_px=millimetres / pixels,
                    detail=f"{room.name or 'room'} {millimetres:.0f} mm over {pixels:.0f} px",
                )
            )
    return samples


def largest_agreeing_cluster(values: list[float], tolerance: float) -> list[float]:
    """The biggest group of samples that agree with each other within ``tolerance``.

    A plain median assumes most samples are right. With only three or four
    dimension strings on a plan that is not a safe assumption, so instead the
    samples vote: whichever group is largest wins, and ties go to the tighter
    group.
    """
    if not values:
        return []
    best: list[float] = []
    for centre in values:
        group = [v for v in values if abs(v - centre) <= tolerance * centre]
        spread = max(group) - min(group)
        best_spread = (max(best) - min(best)) if best else float("inf")
        if len(group) > len(best) or (len(group) == len(best) and spread < best_spread):
            best = group
    return best


def median(values: list[float]) -> float:
    ordered = sorted(values)
    if not ordered:
        raise ValueError("median of an empty list")
    mid = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[mid]
    return (ordered[mid - 1] + ordered[mid]) / 2.0


def plausible_scale_range(
    wall_thickness_px: float, settings: Settings
) -> tuple[float, float]:
    """The mm-per-pixel values a residential wall thickness allows.

    An independent check on the OCR, and a strong one. Whatever the drawing's
    scale, the walls in it are between roughly 60 and 400 mm thick — stud
    partitions at the bottom, rendered masonry at the top. Having *measured*
    those walls in pixels, any scale outside this band would imply a building
    with 2 m or 20 mm walls, so it can be rejected outright.
    """
    thickness = max(0.5, wall_thickness_px)
    return (settings.min_wall_mm / thickness, settings.max_wall_mm / thickness)


def estimate_scale(
    samples: list[ScaleSample],
    settings: Settings,
    plan_width_px: float,
    wall_thickness_px: float | None = None,
) -> ScaleEstimate:
    """Best-fit scale from the available samples, or an honest fallback.

    Order of preference: corroborated OCR dimensions, then the measured wall
    thickness, then an assumed overall plan width. Each step down is reported
    through ``method``, ``confidence`` and ``note`` rather than passed off as
    the same answer.
    """
    low, high = (
        plausible_scale_range(wall_thickness_px, settings)
        if wall_thickness_px
        else (0.0, float("inf"))
    )

    rejected_implausible = [s for s in samples if not (low <= s.mm_per_px <= high)]
    samples = [s for s in samples if low <= s.mm_per_px <= high]

    if not samples:
        return _without_dimensions(
            settings, plan_width_px, wall_thickness_px, bool(rejected_implausible)
        )

    values = [s.mm_per_px for s in samples]
    cluster = largest_agreeing_cluster(values, settings.scale_agreement_tol)
    best = median(cluster)

    agreement = len(cluster) / len(values)
    # One sample can never be corroborated, so it is capped well below certain.
    ceiling = 0.6 if len(cluster) < 2 else 0.98
    confidence = min(ceiling, 0.45 + 0.55 * agreement)

    notes: list[str] = []
    outliers = len(values) - len(cluster)
    if outliers:
        notes.append(
            f"{outliers} of {len(values)} measurements disagreed with the best fit by more "
            f"than {settings.scale_agreement_tol:.0%} and were ignored."
        )
    if rejected_implausible:
        notes.append(
            f"{len(rejected_implausible)} measurement(s) implied an impossible wall thickness "
            "and were ignored."
        )
    if len(cluster) < 2:
        notes.insert(
            0,
            "The scale rests on a single measurement that nothing else corroborates. "
            "Check it against a known length before trusting any dimension.",
        )

    return ScaleEstimate(
        mm_per_px=best,
        confidence=confidence,
        method="ocr-dimensions",
        samples=samples,
        note=" ".join(notes),
    )


def _without_dimensions(
    settings: Settings,
    plan_width_px: float,
    wall_thickness_px: float | None,
    had_implausible: bool,
) -> ScaleEstimate:
    """Scale from the walls themselves, or from an assumed plan width."""
    preamble = (
        "The dimensions read off the plan implied an impossible wall thickness, so they were "
        "discarded. "
        if had_implausible
        else "No dimension text could be matched to a room. "
    )

    if wall_thickness_px and wall_thickness_px > 0:
        return ScaleEstimate(
            mm_per_px=settings.typical_internal_wall_mm / wall_thickness_px,
            confidence=0.35,
            method="wall-thickness",
            note=(
                preamble
                + "The scale is instead inferred from the measured wall thickness, assuming "
                f"the most common wall on the plan is a {settings.typical_internal_wall_mm:.0f} mm "
                "partition. Calibrate against a known length before trusting any measurement."
            ),
        )

    return ScaleEstimate(
        mm_per_px=settings.fallback_plan_width_mm / max(1.0, plan_width_px),
        confidence=0.15,
        method="fallback",
        note=(
            preamble
            + f"The plan is shown at an assumed {settings.fallback_plan_width_mm / 1000:.0f} m "
            "overall width — calibrate against a known length before trusting any measurement."
        ),
    )


def calibrate(pixels: float, millimetres: float) -> float:
    """mm per pixel from one measured length. Used by /api/floorplan/calibrate."""
    if pixels <= 0:
        raise ValueError("Measured pixel length must be positive.")
    if millimetres <= 0:
        raise ValueError("Real-world length must be positive.")
    return millimetres / pixels


def px_to_mm(point: Point, mm_per_px: float) -> Point:
    return (point[0] * mm_per_px, point[1] * mm_per_px)
