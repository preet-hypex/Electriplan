"""Step 3: find the drawing area inside a marketing floor-plan image.

Uploads carry a border rule, an address line, a disclaimer, an agency logo,
generous margins and sometimes dark letterbox bands down the sides. Two passes
narrow that down:

1. *Find the page.* The sheet of white the plan is printed on. Chosen by the
   largest **bounding box** among bright regions rather than the largest pixel
   count, because the page is a frame around the drawing — the drawing's own
   interior can easily hold more white pixels than the margin does, and picking
   by pixel count lands inside the building and crops the outer walls off.
2. *Find the drawing.* Within the page, the bounding box of strokes thick
   enough to be walls. Titles, disclaimers, logos and the border rule are all
   drawn thin, so they fall outside it.

Never raises. An unreliable result is reported through ``confidence`` and
``fallback_reason`` so the UI can offer a manual crop instead of failing.
"""

from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np

from ..config import Settings
from .preprocess import binarise, estimate_wall_thickness


@dataclass
class Region:
    x: int
    y: int
    width: int
    height: int
    confidence: float
    #: Set when a stage was rejected and a looser fallback was used.
    fallback_reason: str | None = None

    @property
    def slice_(self) -> tuple[slice, slice]:
        return slice(self.y, self.y + self.height), slice(self.x, self.x + self.width)

    def crop(self, img: np.ndarray) -> np.ndarray:
        rows, cols = self.slice_
        return img[rows, cols]


def detect_region(gray: np.ndarray, settings: Settings) -> Region:
    """Return the plan region, falling back to the full image when unsure."""
    height, width = gray.shape[:2]
    page, reason = _find_page(gray, settings)

    drawing = _find_drawing(gray, page, settings)
    if drawing is None:
        return Region(
            x=page[0],
            y=page[1],
            width=page[2],
            height=page[3],
            confidence=0.4,
            fallback_reason=(reason or "")
            + " No wall-thick strokes were found, so the whole page is used.",
        )

    x, y, w, h = drawing
    # How much of the page the drawing fills is a reasonable proxy for how
    # confident we should be that we found a floor plan and not a photograph.
    coverage = (w * h) / float(max(1, page[2] * page[3]))
    confidence = float(np.clip(0.55 + 0.4 * min(1.0, coverage * 1.6), 0.0, 0.99))
    if reason:
        confidence = min(confidence, 0.5)

    del height, width
    return Region(x=x, y=y, width=w, height=h, confidence=confidence, fallback_reason=reason)


def _find_page(gray: np.ndarray, settings: Settings) -> tuple[tuple[int, int, int, int], str | None]:
    """The sheet the plan is printed on, as (x, y, w, h)."""
    height, width = gray.shape[:2]
    whole = (0, 0, width, height)

    bright = _paper_mask(gray, settings)
    count, _labels, stats, _centroids = cv2.connectedComponentsWithStats(bright, 8)
    if count <= 1:
        return whole, "No page-coloured background was found; using the whole image."

    min_pixels = settings.region_min_area_frac * width * height * 0.25
    best: tuple[int, int, int, int] | None = None
    best_box_area = 0
    for index in range(1, count):
        x, y, w, h, area = (int(v) for v in stats[index, :5])
        if area < min_pixels:
            continue
        box_area = w * h
        if box_area > best_box_area:
            best, best_box_area = (x, y, w, h), box_area

    if best is None:
        return whole, "No page-sized background was found; using the whole image."
    if best_box_area < settings.region_min_area_frac * width * height:
        return whole, "The page area looked too small to be the drawing; using the whole image."
    return best, None


def _paper_mask(gray: np.ndarray, settings: Settings) -> np.ndarray:
    """Paper versus everything else, split where the image itself divides.

    Otsu picks the level, so nothing is assumed about how bright the paper is
    or how dark the drawing is — a washed-out scan and a crisp export both
    split in the right place. A fixed brightness would only be right for the
    plans it was chosen on.

    The fallback exists for the pathological case where Otsu lands somewhere
    that leaves almost nothing as paper, which means the image is not a
    document at all.
    """
    _, mask = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY | cv2.THRESH_OTSU)
    if np.count_nonzero(mask) < settings.page_min_area_frac * mask.size:
        _, mask = cv2.threshold(gray, settings.page_fallback_level, 255, cv2.THRESH_BINARY)
    return mask


def _find_drawing(
    gray: np.ndarray, page: tuple[int, int, int, int], settings: Settings
) -> tuple[int, int, int, int] | None:
    """Bounding box of the wall-thick ink inside the page, padded a little."""
    px, py, pw, ph = page
    if pw < 10 or ph < 10:
        return None

    patch = gray[py : py + ph, px : px + pw]
    ink = binarise(patch)
    min_run = max(
        settings.min_wall_length_px,
        int(round(min(pw, ph) * settings.min_wall_length_frac)),
    )
    thickness = estimate_wall_thickness(ink, settings, min_run)
    kernel_px = max(
        settings.min_open_kernel_px,
        min(settings.max_open_kernel_px, int(round(thickness * settings.thickness_open_ratio))),
    )
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_px, kernel_px))
    thick = cv2.morphologyEx(ink, cv2.MORPH_OPEN, kernel)

    coordinates = cv2.findNonZero(thick)
    if coordinates is None:
        return None
    x, y, w, h = (int(v) for v in cv2.boundingRect(coordinates))
    if w < 20 or h < 20:
        return None

    pad = int(round(max(4.0, thickness * settings.region_pad_thickness_ratio)))
    x = max(0, x - pad)
    y = max(0, y - pad)
    w = min(pw - x, w + 2 * pad)
    h = min(ph - y, h + 2 * pad)
    return (px + x, py + y, w, h)
