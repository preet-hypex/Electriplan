"""Step 1–2: load an image and normalise it without destroying the original."""

from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np

from ..config import Settings

SUPPORTED_SUFFIXES = {".jpg", ".jpeg", ".png"}


class ImageLoadError(ValueError):
    """The upload could not be decoded as an image."""


@dataclass
class Normalised:
    """The original pixels plus the cleaned-up greyscale we work from."""

    original: np.ndarray  # BGR, untouched
    gray: np.ndarray  # denoised, contrast-normalised greyscale

    @property
    def height(self) -> int:
        return int(self.original.shape[0])

    @property
    def width(self) -> int:
        return int(self.original.shape[1])


def load_image(data: bytes) -> np.ndarray:
    """Decode bytes to BGR. Raises ImageLoadError on anything unreadable."""
    buf = np.frombuffer(data, dtype=np.uint8)
    img = cv2.imdecode(buf, cv2.IMREAD_COLOR)
    if img is None or img.size == 0:
        raise ImageLoadError("Could not decode the upload as a JPG or PNG image.")
    if min(img.shape[:2]) < 200:
        raise ImageLoadError(
            f"Image is only {img.shape[1]}×{img.shape[0]} px. "
            "Floor-plan detection needs at least 200 px on the shorter side."
        )
    return img


def normalise(img: np.ndarray, settings: Settings) -> Normalised:
    """Greyscale, denoise and even out contrast. The original is kept as-is.

    ``settings`` is accepted so callers can tune this stage later; the current
    steps are parameter-free on purpose — anything aggressive here costs wall
    detection more than it gains.
    """
    del settings  # reserved; see docstring

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    # Median blur removes JPEG speckle without softening wall edges the way a
    # Gaussian would.
    gray = cv2.medianBlur(gray, 3)
    # CLAHE rather than global equalisation: floor plans are mostly white, and
    # a global stretch would amplify paper texture into false ink.
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    gray = clahe.apply(gray)
    return Normalised(original=img, gray=gray)


def binarise(gray: np.ndarray) -> np.ndarray:
    """Ink mask: 255 where the drawing is, 0 where the paper is.

    Otsu picks the split point, which handles scans and screenshots alike as
    long as the region passed in is mostly page rather than mostly border.
    """
    _, ink = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)
    return ink


def faint_marks(gray: np.ndarray, settings: Settings, block_px: int) -> np.ndarray:
    """Every mark on the page, however light — not just the wall-black ink.

    Otsu splits the page into "wall-black" and "paper-white", which is what wall
    detection wants and what nothing else does. Plans commonly draw a window as
    a *lighter* band filling the wall line, and Otsu discards it along with the
    paper, leaving a hole where the wall is in fact continuous.

    The threshold is adaptive rather than a fixed brightness, so what counts as
    a mark is decided by local contrast against the surrounding paper. A pale
    line on white and a dark line on a grey scan both register; no assumption is
    made about what shade anything is drawn in.
    """
    block = max(11, int(block_px) | 1)  # odd, and wider than a wall
    return cv2.adaptiveThreshold(
        cv2.medianBlur(gray, 3),
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY_INV,
        block,
        settings.faint_mark_offset,
    )


def linear_structures(ink: np.ndarray, min_run_px: int) -> np.ndarray:
    """Ink that forms long horizontal or vertical runs.

    Walls, and only a little else: text glyphs, furniture symbols and door arcs
    are all too short to survive an opening this long.
    """
    run = max(3, int(min_run_px))
    horizontal = cv2.morphologyEx(
        ink, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (run, 1))
    )
    vertical = cv2.morphologyEx(
        ink, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (1, run))
    )
    return cv2.bitwise_or(horizontal, vertical)


def dominant_stroke_width(ink: np.ndarray, settings: Settings, min_run_px: int) -> float:
    """The most common stroke width among strokes heavy enough to be a wall.

    Wall thickness is measured as a high percentile, which answers "how thick
    are the thickest walls" — the right question for the tolerances that scale
    off it, and the wrong one for sizing the opening that removes everything
    thinner than a wall. A plan drawn with two weights, heavy for the outer
    walls and lighter for the partitions, puts that percentile on the heavy
    ones, and the opening then erases every partition in the house. On one test
    plan the estimate is 12 px, the kernel 7 px, and the internal walls 6 px:
    the filter is wider than the thing it is meant to keep.

    So the kernel is sized from the *commonest* stroke instead, which on a
    two-weight plan is the lighter wall, since a house has more partitions than
    it has outside walls.

    Measured on ridge pixels — the local maxima of the distance transform,
    which are the stroke centre lines — so every stroke contributes its own
    width once, rather than a ramp of values from nothing up to it. Hairlines
    are excluded first: leader lines, hatching and furniture outlines are
    drawn thinner than any wall, and on some plans there are more of them than
    there are walls.
    """
    linear = linear_structures(ink, min_run_px)
    distances = cv2.distanceTransform(linear, cv2.DIST_L2, 5)
    ridge = cv2.dilate(distances, np.ones((3, 3), np.uint8))
    widths = distances[(distances > 0) & (distances >= ridge - 1e-6)] * 2.0
    if widths.size == 0:
        return settings.min_wall_thickness_px

    counts = np.bincount(np.rint(widths).astype(np.intp))
    floor = int(settings.hairline_max_px) + 1
    if counts.size <= floor:
        return settings.min_wall_thickness_px
    counts[:floor] = 0
    if not counts.any():
        return settings.min_wall_thickness_px
    return float(counts.argmax())


def estimate_wall_thickness(ink: np.ndarray, settings: Settings, min_run_px: int) -> float:
    """Estimate wall thickness in pixels.

    The distance transform gives every ink pixel its distance to the nearest
    background pixel, so twice a high percentile of that is the width of the
    fattest strokes.

    Crucially the percentile is taken over *long runs* only, not over all ink.
    Measuring all ink assumes walls are the thickest thing on the page, which
    holds on a large print but not on a low-resolution one: there a wall is four
    pixels across and lettering is three, so text drags the estimate up, the
    opening kernel that follows is sized to erase four-pixel strokes, and the
    walls vanish along with the text. Long runs are walls almost by definition,
    whatever the resolution.
    """
    linear = linear_structures(ink, min_run_px)
    distances = cv2.distanceTransform(linear, cv2.DIST_L2, 5)
    values = distances[distances > 0]
    if values.size == 0:
        # Nothing linear at all: fall back to the whole ink mask so a plan drawn
        # in some unexpected way still gets a number rather than a crash.
        distances = cv2.distanceTransform(ink, cv2.DIST_L2, 5)
        values = distances[distances > 0]
    if values.size == 0:
        return settings.min_wall_thickness_px
    half = float(np.percentile(values, settings.thickness_percentile))
    return max(settings.min_wall_thickness_px, half * 2.0)
