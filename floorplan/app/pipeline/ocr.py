"""Step 6: read the text on the plan.

Runs independently of wall detection — the two stages never see each other's
output — and returns every word Tesseract found with a bounding box and a
confidence, classified as a room name, a dimension string or neither.

Nothing here assumes the OCR is right. A word that matches nothing keeps its
text and is exported as an ``other`` label for the user to deal with.
"""

from __future__ import annotations

import difflib
import re
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, replace

import cv2
import numpy as np
import pytesseract

from ..config import DEFAULTS, ROOM_NAME_DICTIONARY, ROOM_NAME_STOPWORDS, Settings

Point = tuple[float, float]
_MapBack = Callable[["BBox"], "BBox"]


@dataclass(frozen=True)
class BBox:
    x: float
    y: float
    width: float
    height: float

    @property
    def centre(self) -> Point:
        return (self.x + self.width / 2.0, self.y + self.height / 2.0)


@dataclass
class TextItem:
    """One line of recognised text, in plan-region pixels."""

    text: str
    bbox: BBox
    confidence: float
    kind: str = "other"  # "room" | "dimension" | "other"
    #: Canonical room name, when ``kind`` is "room".
    room_name: str | None = None
    #: Parsed millimetre pair, when ``kind`` is "dimension".
    dimensions_mm: tuple[float, float] | None = None
    #: True when this was read off a turned page, i.e. printed up the page.
    rotated: bool = False


class OcrUnavailable(RuntimeError):
    """Tesseract is not installed or not on PATH."""


#: "2.5m X 3.5m", "2.5 x 3.5 m", "2500 X 3500", "3.5m×4.0m".
_DIMENSION_RE = re.compile(
    r"(?P<a>\d+(?:[.,]\d+)?)\s*(?P<au>mm|m)?\s*[x×*]\s*(?P<b>\d+(?:[.,]\d+)?)\s*(?P<bu>mm|m)?",
    re.IGNORECASE,
)


def extract_text(
    gray_region: np.ndarray, settings: Settings, thickness_px: float | None = None
) -> list[TextItem]:
    """OCR the plan region and return classified lines.

    The region is upscaled first: label text on an estate-agent plan is often
    only ten or twelve pixels tall, which is below what Tesseract reads well.

    Tesseract reads along rows, so a label printed up the page — the narrow
    rooms, ``LINEN``, ``PTY``, ``WC``, ``ROBE`` — is invisible to it. The page
    is therefore read three times, once upright and once turned each way, and
    each turned reading has its boxes mapped back into the upright frame.

    Turning the page also drags every *horizontal* label onto its side, so the
    turned passes return a great deal of nonsense alongside the one thing they
    were run for — on one test plan the disclaimer footer, read sideways,
    fuzzy-matched to POWDER and to ENS. Three guards, and a turned reading has
    to pass all of them. It must have been recognised: matched to the room
    dictionary, or parsed as a dimension. It must sit where nothing was read
    upright, so a turned pass can add a label but never overrule one. And it
    must be *confident*, which is what separates the two cases cleanly — the
    genuine vertical labels come back at 0.92 and above, the sideways
    disclaimer at 0.65 and below.
    """
    height, width = gray_region.shape[:2]

    # Each pass is a pure function of the page, so they are prepared together
    # and run together. Tesseract is a separate process, so the waiting is all
    # done outside the interpreter and threads genuinely overlap: four passes
    # cost about as long as the slowest one rather than the sum.
    passes: list[tuple[np.ndarray, _MapBack | None]] = [(gray_region, None)]

    # Plans draw over their own labels: a door arc sweeps across WIR, a leader
    # line crosses a dimension, hatching runs under a name. Those strokes are
    # all thinner than the lettering, so opening the ink with a small kernel
    # takes them off and leaves the glyphs. On plan 5 that is the difference
    # between reading nothing where WIR is written and reading it at 95%.
    if settings.ocr_declutter and thickness_px:
        passes.append((_without_thin_marks(gray_region, settings, thickness_px), None))

    if settings.ocr_read_rotated:
        passes.append(
            (
                cv2.rotate(gray_region, cv2.ROTATE_90_CLOCKWISE),
                lambda b: BBox(b.y, height - (b.x + b.width), b.height, b.width),
            )
        )
        passes.append(
            (
                cv2.rotate(gray_region, cv2.ROTATE_90_COUNTERCLOCKWISE),
                lambda b: BBox(width - (b.y + b.height), b.x, b.height, b.width),
            )
        )

    if len(passes) == 1:
        return _read(gray_region, settings)

    with ThreadPoolExecutor(max_workers=len(passes)) as pool:
        readings = list(pool.map(lambda p: _read(p[0], settings), passes))

    # Merged in a fixed order, never in the order they happened to finish: the
    # first reading of a spot is the one kept, so completion order would decide
    # the result and the same page would not analyse the same way twice.
    items = readings[0]
    for (_, back), reading in zip(passes[1:], readings[1:]):
        for item in reading:
            if back is not None:
                item = replace(item, bbox=back(item.bbox), rotated=True)
            _admit(items, item, settings)
    return items


def _admit(items: list[TextItem], item: TextItem, settings: Settings) -> None:
    """Take an extra reading only if it adds something, and is sure of it.

    Every pass after the first is speculative — the page turned on its side, or
    with ink filed off it — so all three guards apply: the reading has to have
    been recognised, it has to be confident, and it has to sit where nothing was
    read already. An extra pass can add a label; it never overrules one.
    """
    if item.kind == "other":
        return
    if item.confidence < settings.ocr_rotated_min_confidence:
        return
    if any(_overlaps(item.bbox, seen.bbox) for seen in items):
        return
    items.append(item)


def _without_thin_marks(
    gray_region: np.ndarray, settings: Settings, thickness_px: float
) -> np.ndarray:
    """The page with strokes thinner than its lettering opened away."""
    size = max(2, int(round(settings.ocr_declutter_thickness_ratio * thickness_px)))
    _, ink = cv2.threshold(gray_region, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (size, size))
    return 255 - cv2.morphologyEx(ink, cv2.MORPH_OPEN, kernel)


def _read(image: np.ndarray, settings: Settings) -> list[TextItem]:
    """One pass of Tesseract over an image, grouped into classified lines."""
    scale = max(1.0, settings.ocr_upscale)
    scaled = cv2.resize(image, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)

    try:
        data = pytesseract.image_to_data(
            scaled,
            config=f"--psm {settings.ocr_psm}",
            output_type=pytesseract.Output.DICT,
        )
    except pytesseract.TesseractNotFoundError as exc:  # pragma: no cover - env dependent
        raise OcrUnavailable(
            "Tesseract is not installed. Install it (brew install tesseract) and retry."
        ) from exc

    words = _collect_words(data, scale, settings)
    return [_classify(line, settings) for line in _group_lines(words)]


def _overlaps(a: BBox, b: BBox) -> bool:
    """Do two boxes cover any of the same page?"""
    return (
        a.x < b.x + b.width
        and b.x < a.x + a.width
        and a.y < b.y + b.height
        and b.y < a.y + a.height
    )


@dataclass
class _Word:
    text: str
    bbox: BBox
    confidence: float
    line_key: tuple[int, int, int, int]


def _collect_words(data: dict, scale: float, settings: Settings) -> list[_Word]:
    words: list[_Word] = []
    for index, raw in enumerate(data["text"]):
        text = raw.strip()
        if not text:
            continue
        try:
            confidence = float(data["conf"][index]) / 100.0
        except (TypeError, ValueError):
            continue
        if confidence < settings.ocr_min_word_confidence:
            continue
        words.append(
            _Word(
                text=text,
                bbox=BBox(
                    x=data["left"][index] / scale,
                    y=data["top"][index] / scale,
                    width=data["width"][index] / scale,
                    height=data["height"][index] / scale,
                ),
                confidence=confidence,
                line_key=(
                    data["page_num"][index],
                    data["block_num"][index],
                    data["par_num"][index],
                    data["line_num"][index],
                ),
            )
        )
    return words


def _group_lines(words: list[_Word]) -> list[list[_Word]]:
    """Join words into lines.

    Tesseract's own line numbering is the first cut, but in sparse-text mode it
    puts almost every word in its own block, so words that sit on the same
    baseline and nearly touch are merged afterwards. "BED" and "3" have to end
    up as one label.
    """
    by_key: dict[tuple[int, int, int, int], list[_Word]] = {}
    for word in words:
        by_key.setdefault(word.line_key, []).append(word)

    lines = [sorted(group, key=lambda w: w.bbox.x) for group in by_key.values()]

    merged = True
    while merged:
        merged = False
        for i in range(len(lines)):
            for j in range(i + 1, len(lines)):
                if _same_line(lines[i], lines[j]):
                    lines[i] = sorted(lines[i] + lines[j], key=lambda w: w.bbox.x)
                    del lines[j]
                    merged = True
                    break
            if merged:
                break

    return lines


def _same_line(a: list[_Word], b: list[_Word]) -> bool:
    box_a, box_b = _line_bbox(a), _line_bbox(b)
    height = min(box_a.height, box_b.height)
    if height <= 0:
        return False
    # Baselines within half a character height.
    if abs(box_a.centre[1] - box_b.centre[1]) > height * 0.6:
        return False
    # And a horizontal gap no wider than about two characters.
    gap = max(box_a.x - (box_b.x + box_b.width), box_b.x - (box_a.x + box_a.width))
    return gap <= height * 1.2


def _line_bbox(words: list[_Word]) -> BBox:
    left = min(w.bbox.x for w in words)
    top = min(w.bbox.y for w in words)
    right = max(w.bbox.x + w.bbox.width for w in words)
    bottom = max(w.bbox.y + w.bbox.height for w in words)
    return BBox(left, top, right - left, bottom - top)


def _classify(line: list[_Word], settings: Settings) -> TextItem:
    text = " ".join(w.text for w in line)
    bbox = _line_bbox(line)
    confidence = sum(w.confidence for w in line) / len(line)

    dimensions = parse_dimensions(text, settings)
    if dimensions is not None:
        return TextItem(
            text=text,
            bbox=bbox,
            confidence=confidence,
            kind="dimension",
            dimensions_mm=dimensions,
        )

    name, ratio = match_room_name(text, settings)
    if name is not None:
        return TextItem(
            text=text,
            bbox=bbox,
            # A fuzzy match is less certain than an exact one, and the OCR's own
            # confidence caps it either way.
            confidence=confidence * ratio,
            kind="room",
            room_name=name,
        )

    return TextItem(text=text, bbox=bbox, confidence=confidence, kind="other")


def parse_dimensions(text: str, settings: Settings | None = None) -> tuple[float, float] | None:
    """Parse "2.5m X 3.5m" and friends into a millimetre pair.

    A bare number pair without units is read as metres when both values are
    plausible room sizes, and as millimetres otherwise.

    Two things guard against low-resolution misreads. First, a decimal point is
    a single pixel and is the first thing to disappear on a small print, so
    "4.2m X 3.0m" routinely comes back as "42m X 30m"; a whole number of metres
    at or above ``decimal_repair_min_m`` is therefore read as that number with
    the point put back. Second, the result must be a size a room can actually
    be — the misreads that this does not repair are rejected rather than
    passed on to the scale estimator, which would otherwise size the whole plan
    from them.
    """
    settings = settings or DEFAULTS
    match = _DIMENSION_RE.search(text)
    if match is None:
        return None

    def value(raw: str, unit: str | None) -> float | None:
        try:
            number = float(raw.replace(",", "."))
        except ValueError:
            return None
        if number <= 0:
            return None

        lowered = (unit or "").lower()
        if lowered == "mm":
            return number
        # No unit given: 2.5 means metres, 2500 means millimetres.
        if lowered != "m" and number >= 100:
            return number

        written_with_decimal = "." in raw or "," in raw
        if not written_with_decimal and number >= settings.decimal_repair_min_m:
            number /= 10.0  # the decimal point did not survive the scan
        return number * 1000.0

    unit_a = match.group("au") or match.group("bu")
    unit_b = match.group("bu") or match.group("au")
    a = value(match.group("a"), unit_a)
    b = value(match.group("b"), unit_b)
    if a is None or b is None:
        return None

    low, high = settings.min_room_dimension_mm, settings.max_room_dimension_mm
    if not (low <= a <= high and low <= b <= high):
        return None
    return (a, b)


def normalise_name(text: str) -> str:
    """Uppercase, strip decoration, keep digits and apostrophes."""
    cleaned = re.sub(r"[^A-Za-z0-9' ]+", " ", text.upper())
    tokens = [t for t in cleaned.split() if t and t not in ROOM_NAME_STOPWORDS]
    return " ".join(tokens)


def match_room_name(text: str, settings: Settings) -> tuple[str | None, float]:
    """Match OCR text against the room-name dictionary.

    Returns the canonical name and a 0–1 similarity. Exact matches score 1.0;
    a close miss ("L'ORY" for "L'DRY", "BATI I" for "BATH") is accepted above
    the configured ratio so a single misread character does not lose the label.
    """
    key = normalise_name(text)
    if not key:
        return (None, 0.0)

    if key in ROOM_NAME_DICTIONARY:
        return (ROOM_NAME_DICTIONARY[key], 1.0)

    # "MASTER BED 3.5m" — try the leading words too.
    tokens = key.split()
    for count in range(len(tokens), 0, -1):
        prefix = " ".join(tokens[:count])
        if prefix in ROOM_NAME_DICTIONARY:
            return (ROOM_NAME_DICTIONARY[prefix], 1.0 if count == len(tokens) else 0.92)

    best_name: str | None = None
    best_ratio = settings.room_name_match_ratio
    for candidate, canonical in ROOM_NAME_DICTIONARY.items():
        ratio = difflib.SequenceMatcher(None, key, candidate).ratio()
        if ratio > best_ratio:
            best_ratio, best_name = ratio, canonical
    if best_name is None:
        return (None, 0.0)
    return (best_name, best_ratio)
