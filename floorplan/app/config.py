"""Every tunable threshold in the detection pipeline, in one place.

Values are either absolute pixels, fractions of the image's smaller dimension
(suffixed ``_frac``), or ratios of an already-derived quantity. Anything derived
from the image itself is computed in :func:`derive` so the pipeline stays
resolution-independent.
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace


@dataclass(frozen=True)
class Settings:
    # -- plan region -------------------------------------------------------
    #: Paper is separated from the drawing by Otsu, not by a fixed brightness,
    #: so nothing is assumed about how light the page is. These two only cover
    #: the case where that split leaves almost no paper at all.
    page_min_area_frac: float = 0.2
    page_fallback_level: int = 200
    #: Local contrast, in grey levels, needed to count as a mark on the page.
    #: Used by the adaptive threshold in preprocess.faint_marks; relative, so
    #: it does not assume anything about how dark the drawing is.
    faint_mark_offset: int = 8
    #: Adaptive-threshold window, as a multiple of wall thickness.
    faint_mark_block_ratio: float = 4.0
    #: The detected region must cover at least this fraction of the image,
    #: otherwise the whole image is used and the user is warned.
    region_min_area_frac: float = 0.15
    #: Margin left around the drawing, in wall thicknesses, so a wall on the
    #: very edge is not clipped by the crop.
    region_pad_thickness_ratio: float = 2.0

    # -- wall detection ----------------------------------------------------
    #: Percentile of the distance transform, measured over long runs only,
    #: used to estimate wall thickness. See preprocess.estimate_wall_thickness
    #: for why the sample is restricted to long runs.
    thickness_percentile: float = 85.0
    #: Opening kernel size as a ratio of the estimated wall thickness. Strokes
    #: thinner than this are removed before any line finding happens.
    thickness_open_ratio: float = 0.55
    min_open_kernel_px: int = 3
    max_open_kernel_px: int = 21
    #: Strokes this thin are annotation — leader lines, hatching, furniture
    #: outlines — never a wall, and are ignored when looking for the commonest
    #: wall width. See preprocess.dominant_stroke_width.
    hairline_max_px: int = 3
    #: Hard bounds on the estimated wall thickness, in pixels.
    min_wall_thickness_px: float = 3.0
    max_wall_thickness_px: float = 60.0

    #: Shortest bar kept as a possible door jamb, in wall thicknesses. Jambs
    #: are far shorter than any wall, so they need their own floor; see
    #: geometry.readmit_stubs for the rule that decides which are real.
    stub_min_thickness_ratio: float = 0.9
    #: A jamb is never much thicker than the wall it interrupts. Without a
    #: ceiling, round fittings traced as bars report their diameter as a
    #: thickness and are admitted as walls.
    stub_max_thickness_ratio: float = 1.8
    #: How far a stub's thickness may differ from its wall's, as a factor.
    stub_thickness_match: float = 1.5
    #: A stub must be at least this many times longer than it is thick.
    stub_min_aspect: float = 1.0
    #: How close a stub's end must come to a wall to count as returning off it.
    stub_touch_thickness_ratio: float = 1.2
    #: Shortest run kept by the directional opening. A wall must be this many
    #: of its *own* thicknesses long, so the floor belongs to the drawing
    #: rather than to the image: two plans can arrive the same pixel size and
    #: still be drawn at quite different weights.
    min_wall_length_thickness_ratio: float = 2.2
    #: Fraction of the smaller image dimension, used only to bootstrap — the
    #: thickness is not known yet at the point it is first needed.
    min_wall_length_frac: float = 0.02
    min_wall_length_px: int = 14
    #: While tracing a bar, how far its centre may wander between adjacent
    #: columns, as a ratio of its thickness.
    track_centre_tol_ratio: float = 0.75
    #: Columns of missing pixels tolerated inside one bar.
    track_gap_px: int = 3
    #: Fraction of a segment's bounding box that must be ink for it to be
    #: treated as a solid wall rather than a stray.
    min_fill_ratio: float = 0.45

    # -- geometry cleanup --------------------------------------------------
    #: Walls within this many degrees of an axis are snapped square.
    axis_snap_deg: float = 2.5
    #: A wall is also snapped square when its cross-axis drift is smaller than
    #: this, which catches short stubs whose few-pixel lean is a large angle.
    axis_snap_thickness_ratio: float = 0.5
    axis_snap_min_px: float = 2.0
    #: Maximum angle between two walls that could still be one wall. Loose on
    #: purpose — see is_collinear, where the perpendicular offset decides.
    collinear_angle_tol_deg: float = 12.0
    #: Perpendicular separation allowed between collinear walls, as a ratio of
    #: their thickness.
    collinear_offset_ratio: float = 0.75
    #: End-to-end gap that still counts as one wall, as a ratio of thickness.
    #: Deliberately small: a doorway is a gap that must survive.
    merge_gap_ratio: float = 1.5
    #: Endpoints closer than this are pulled onto a shared node.
    endpoint_snap_ratio: float = 1.6
    #: A near-miss T-junction within this distance is closed by extending.
    junction_extend_ratio: float = 2.0
    #: Node coordinates within this many wall-thicknesses of each other are
    #: pulled onto one shared x (or y) line, squaring the whole plan at once.
    axis_align_thickness_ratio: float = 0.5

    #: How close two walls must come to count as connected, in thicknesses.
    furniture_touch_ratio: float = 1.5

    # -- room detection ----------------------------------------------------
    #: Gap widths are expressed as multiples of the estimated wall thickness
    #: rather than in millimetres, because rooms have to be found before the
    #: scale can be estimated from them — and the ratio of a door's width to a
    #: wall's thickness is roughly constant across residential plans (a 900 mm
    #: door in a 90–110 mm stud wall is about ten wall-thicknesses wide).
    max_doorway_thickness_ratio: float = 11.0
    #: Narrowest gap that can be an opening, in wall thicknesses. Below this
    #: it is a crack between two fragments of one wall, not a doorway.
    min_gap_thickness_ratio: float = 1.0

    #: A wider collinear gap is bridged only when both facing endpoints are
    #: dangling — a garage door or a wide cased opening.
    max_opening_thickness_ratio: float = 30.0
    #: Absolute ceiling on either, so a bad thickness estimate cannot staple
    #: the whole drawing together.
    max_opening_frac: float = 0.3
    #: A bridge must run within this many degrees of an axis, so it cannot cut
    #: a small room from corner to corner.
    bridge_axis_tol_deg: float = 22.0
    #: A wall end counts as facing a gap when no wall leaves it within this
    #: angle of the gap's direction.
    bridge_free_angle_deg: float = 50.0

    # -- openings (doors and windows) --------------------------------------
    #: Fraction of a gap that must be spanned by one unbroken line parallel to
    #: the wall for the gap to be a window rather than a hole. Shape, not
    #: shade — see openings._line_continues.
    window_min_run: float = 0.75
    #: Proportion of the span a run may skip and still count as unbroken.
    window_run_gap_tolerance: float = 0.06

    #: A sliding door is two panels passing one another, which reads as a
    #: window to a test that only asks whether *a* line spans the gap. These
    #: describe the two-panel shape: see openings._sliding_span.
    #: Above this, one line already spans the opening, so it is a window.
    sliding_max_line_run: float = 0.90
    #: Each panel covers this much of the opening on its own.
    sliding_panel_min_run: float = 0.30
    sliding_panel_max_run: float = 0.88
    #: How far apart across the wall the two panels must sit, in thicknesses.
    sliding_panel_offset_ratio: float = 0.45
    #: How far apart along the opening their midpoints must be.
    sliding_panel_shift: float = 0.18
    #: How much of the opening the pair must cover between them.
    sliding_min_span: float = 0.70
    #: Cleanup's final pass keeps a bar only if it is longer than it is thick.
    #: Guards against slivers left by snapping without discarding jamb returns.
    sliver_aspect: float = 1.0
    #: ...and no shorter than this fraction of the minimum wall length, so the
    #: aspect test stays meaningful when walls are only a few pixels thick.
    sliver_length_ratio: float = 0.6

    #: Sampling across the wall when looking for the panels.
    sliding_band_ratio: float = 1.3
    sliding_offset_samples: int = 17

    # --- Garage doors -------------------------------------------------------
    #: A garage door is drawn as a dashed rectangle across the opening. What
    #: identifies it is not that a line is there but that the line is *broken*
    #: at a regular period, so these count dashes rather than measure coverage.
    #: How many separate dashes make a dashed line rather than a broken one.
    garage_min_dashes: int = 12
    #: Dash period as a multiple of wall thickness. A dashed line repeats about
    #: once per wall thickness; anything sparser is a line with gaps in it.
    garage_max_period_ratio: float = 1.4
    #: A dashed line is part ink, part paper. Outside this band it is a solid
    #: line (a window) or a few stray marks.
    garage_min_coverage: float = 0.15
    garage_max_coverage: float = 0.90
    #: The dashes have to run the length of the opening, not cluster at one end.
    garage_min_span: float = 0.75
    #: Sampling across and along the gap when looking for the dashes.
    garage_band_ratio: float = 1.4
    garage_offset_samples: int = 21
    garage_samples_per_px: int = 2
    #: Points sampled along a candidate door swing and along its leaf.
    door_arc_samples: int = 24
    door_leaf_samples: int = 14
    #: Fraction of the leaf's length skipped nearest the hinge, where the wall
    #: itself is inked and would score for any opening at all.
    door_leaf_skip: float = 0.25
    #: A door needs the arc that closes from the leaf's tip back to the far
    #: jamb. The leaf is the less reliable half — it is short, and it lands
    #: under fittings — so it is not required, but a leaf this clear settles
    #: the classification outright ahead of the window test.
    door_min_arc: float = 0.45
    door_decisive_leaf: float = 0.7
    #: Angles either side of square that the leaf is looked for at, to absorb
    #: the few pixels of error in a detected jamb.
    door_leaf_angle_tol_deg: float = 12.0

    #: Smallest region kept, as a fraction of the plan region's area.
    min_room_area_frac: float = 0.0012
    #: Largest region kept — anything bigger is the outside leaking in.
    max_room_area_frac: float = 0.55
    #: Contour simplification tolerance, as a ratio of wall thickness.
    simplify_ratio: float = 0.9
    #: Polygon vertices closer than this on one axis are squared up.
    polygon_axis_snap_ratio: float = 1.2

    # -- OCR ---------------------------------------------------------------
    ocr_upscale: float = 2.0
    #: Tesseract page segmentation mode. 11 = sparse text, which suits labels
    #: scattered across a drawing.
    #: Also read the page turned each way, to catch labels printed up the page.
    #: Also read the page with thin strokes opened away, so a door arc drawn
    #: across a label stops hiding it.
    ocr_declutter: bool = True
    #: Opening kernel for that pass, as a fraction of wall thickness — the only
    #: measure of the drawing's own scale available at this point.
    ocr_declutter_thickness_ratio: float = 0.25
    ocr_read_rotated: bool = True
    #: A turned reading is speculative, so it has to be confident. Genuine
    #: vertical labels score 0.92+; text from a sideways disclaimer, 0.65-.
    ocr_rotated_min_confidence: float = 0.80
    ocr_psm: int = 11
    #: Word-level confidence below this is discarded outright.
    ocr_min_word_confidence: float = 0.35
    #: Unmatched text needs at least this many alphanumeric characters and
    #: this much confidence to be kept as a label; below that it is a fragment
    #: read out of a furniture symbol, not a label.
    ocr_min_other_characters: int = 3
    ocr_min_other_confidence: float = 0.55
    #: A whole number of metres at or above this is read as a misread decimal
    #: ("42m" for 4.2 m) — the decimal point is one pixel and is the first
    #: thing to disappear on a low-resolution print.
    decimal_repair_min_m: float = 15.0
    #: A parsed dimension outside this range is not a room and is discarded.
    min_room_dimension_mm: float = 300.0
    max_room_dimension_mm: float = 30000.0
    #: Similarity required to accept a fuzzy match against the room-name
    #: dictionary.
    room_name_match_ratio: float = 0.78
    #: How far a label may sit from a room's polygon and still be adopted, as a
    #: fraction of the plan region's smaller dimension.
    label_room_max_dist_frac: float = 0.05

    # -- scale -------------------------------------------------------------
    #: Two scale samples agree if they are within this fraction of each other.
    scale_agreement_tol: float = 0.12
    #: A room's measured shape must match the shape its label claims to within
    #: this fraction, or the pairing is rejected. Catches a dimension string
    #: that landed inside a merged open-plan region rather than its own room.
    scale_aspect_tol: float = 0.3
    #: Residential walls run from stud partitions to rendered masonry. Any
    #: scale implying a thickness outside this band is impossible, whatever the
    #: OCR read. See scale.plausible_scale_range.
    min_wall_mm: float = 60.0
    max_wall_mm: float = 400.0
    #: The most numerous wall on a house plan is the internal partition. When
    #: no dimension survives, its measured pixel thickness gives the scale.
    typical_internal_wall_mm: float = 110.0
    #: Last resort, when there are no walls to measure either: assume the
    #: drawing spans this many millimetres, so the editor still opens.
    fallback_plan_width_mm: float = 15000.0

    # -- reporting ---------------------------------------------------------
    #: Residual thick ink not explained by horizontal or vertical walls, above
    #: which the user is warned about possible diagonal walls.
    diagonal_residual_warn_frac: float = 0.12
    low_wall_confidence_warn: float = 0.6
    #: Fewer detected walls than this means the detection almost certainly
    #: failed, and the user is offered a retry or manual tracing.
    min_expected_walls: int = 6


DEFAULTS = Settings()


@dataclass(frozen=True)
class Derived:
    """Per-image quantities computed once and threaded through the pipeline."""

    #: Estimated wall thickness in pixels.
    wall_thickness_px: float
    #: Morphological opening kernel that keeps walls and drops thin strokes.
    open_kernel_px: int
    #: Shortest wall segment kept.
    min_wall_length_px: int
    #: Plan region dimensions.
    width: int
    height: int

    @property
    def min_dim(self) -> int:
        return min(self.width, self.height)

    @property
    def area(self) -> int:
        return self.width * self.height


def derive(
    settings: Settings,
    width: int,
    height: int,
    thickness_px: float,
    stroke_px: float | None = None,
) -> Derived:
    """Turn the image-independent settings into concrete pixel thresholds.

    ``thickness_px`` is how thick the *thickest* walls are, and everything that
    scales off a wall uses it. ``stroke_px`` is how thick the *commonest* wall
    is, and only the opening kernel uses it. On a plan drawn with one weight
    they are the same number; on a plan drawn with heavy outer walls and
    lighter partitions they are not, and sizing the opening off the thick ones
    erases the partitions. The kernel therefore follows whichever is smaller.
    """
    thickness = float(
        min(settings.max_wall_thickness_px, max(settings.min_wall_thickness_px, thickness_px))
    )
    for_kernel = thickness if stroke_px is None else min(thickness, max(1.0, stroke_px))
    kernel = int(round(for_kernel * settings.thickness_open_ratio))
    kernel = max(settings.min_open_kernel_px, min(settings.max_open_kernel_px, kernel))
    # An even kernel has no centre pixel; morphology behaves more predictably odd.
    if kernel % 2 == 0:
        kernel += 1
    if thickness_px > 0:
        # Relative to the drawing's own walls, not to the image. Two plans can
        # arrive at identical pixel sizes and still be drawn at quite different
        # weights; what counts as "too short to be a wall" belongs to the
        # drawing, so it is expressed in thicknesses like every other tolerance
        # here. The image fraction survives only as the bootstrap below, where
        # the thickness is not known yet because measuring it is what the value
        # is needed for.
        min_len = max(
            settings.min_wall_length_px,
            int(round(thickness * settings.min_wall_length_thickness_ratio)),
        )
    else:
        min_len = max(
            settings.min_wall_length_px,
            int(round(min(width, height) * settings.min_wall_length_frac)),
        )
    return Derived(
        wall_thickness_px=thickness,
        open_kernel_px=kernel,
        min_wall_length_px=min_len,
        width=width,
        height=height,
    )


def with_overrides(**kwargs: object) -> Settings:
    """Build a Settings from the defaults, overriding named fields."""
    return replace(DEFAULTS, **kwargs)  # type: ignore[arg-type]


#: Names for things that live *inside* a room rather than being one: a robe
#: recess, a linen cupboard, a shower. Where room detection resolves them as
#: their own region they are named normally, but where it does not, the label
#: falls inside the surrounding room — and a bedroom should stay BED 4 rather
#: than becoming BIR just because the robe label happened to be read more
#: confidently. See orchestrator._assign_names.
SECONDARY_ROOM_NAMES: frozenset[str] = frozenset(
    {"BIR", "WIR", "ROBE", "LIN", "SHOWER", "STORE", "VOID", "NOOK", "STAIRS"}
)


#: Room labels the OCR stage recognises, mapped to the name written into the
#: model. Add a line here to teach the pipeline a new abbreviation.
ROOM_NAME_DICTIONARY: dict[str, str] = {
    "BED": "BED",
    "BED 1": "BED 1",
    "BED 2": "BED 2",
    "BED 3": "BED 3",
    "BED 4": "BED 4",
    "BEDROOM": "BEDROOM",
    "MASTER": "MASTER BED",
    "MASTER BED": "MASTER BED",
    "MASTER BEDROOM": "MASTER BED",
    "LIVING": "LIVING",
    "LOUNGE": "LOUNGE",
    "FAMILY": "FAMILY",
    "DINING": "DINING",
    "MEALS": "MEALS",
    "KITCHEN": "KITCHEN",
    "GARAGE": "GARAGE",
    "BATH": "BATH",
    "BATHROOM": "BATH",
    "ENS": "ENS",
    "ENSUITE": "ENS",
    "WC": "WC",
    "TOILET": "WC",
    "PDR": "POWDER",
    "POWDER": "POWDER",
    "LDRY": "L'DRY",
    "L'DRY": "L'DRY",
    "LAUNDRY": "L'DRY",
    "WIR": "WIR",
    # Built-in robe. Drawn on nearly every Australian bedroom and, until now,
    # simply absent from this list: the label was read and shown, but never
    # recognised, so the robe came back as an unnamed room.
    "BIR": "BIR",
    "BIRS": "BIR",
    "B.I.R": "BIR",
    "B.I.R.": "BIR",
    "BUILT IN ROBE": "BIR",
    "WIP": "WIP",
    "PANTRY": "PTY",
    "WALK IN ROBE": "WIR",
    "THEATRE": "THEATRE",
    "ACTIVITY": "ACTIVITY",
    "RETREAT": "RETREAT",
    "LEISURE": "LEISURE",
    "SITTING": "SITTING",
    "BALCONY": "BALCONY",
    "COURTYARD": "COURTYARD",
    "HALLWAY": "HALL",
    "PASSAGE": "HALL",
    "LDY": "L\'DRY",
    "LAUNDRY": "L\'DRY",
    "TOILET": "WC",
    "SHR": "SHOWER",
    "SHOWER": "SHOWER",
    "LINEN CUPBOARD": "LIN",
    "STORAGE": "STORE",
    "STUDIO": "STUDIO",
    "OFFICE": "OFFICE",
    "NOOK": "NOOK",
    "VOID": "VOID",
    "STAIRS": "STAIRS",
    "DRIVEWAY": "DRIVEWAY",
    "CARPORT": "CARPORT",

    "ROBE": "ROBE",
    "WIL": "WIR",
    "PTY": "PTY",
    "PANTRY": "PTY",
    "LIN": "LIN",
    "LINEN": "LIN",
    "ENTRY": "ENTRY",
    "PORCH": "PORCH",
    "HALL": "HALL",
    "STUDY": "STUDY",
    "RUMPUS": "RUMPUS",
    "ALFRESCO": "ALFRESCO",
    "STORE": "STORE",
    "DECK": "DECK",
    "PATIO": "PATIO",
    "VERANDAH": "VERANDAH",
}

#: Tokens that appear inside a room label but are not a name on their own.
ROOM_NAME_STOPWORDS: frozenset[str] = frozenset({"THE", "OF", "AND"})


@dataclass(frozen=True)
class PipelineConfig:
    """What the API hands the pipeline for a single request."""

    settings: Settings = field(default_factory=lambda: DEFAULTS)
    #: Skip scale estimation and use this instead, in mm per source pixel.
    mm_per_px_override: float | None = None
