"""Reading the text on a plan, including labels printed up the page."""

from __future__ import annotations

import dataclasses

import cv2
import numpy as np
import pytest

from app.config import DEFAULTS
from app.pipeline.ocr import BBox, TextItem, extract_text


def page(labels: list[tuple[str, tuple[int, int], bool]]) -> np.ndarray:
    """A white page carrying each label, upright or turned up the page."""
    img = np.full((520, 620), 255, np.uint8)
    for text, (x, y), vertical in labels:
        if not vertical:
            cv2.putText(img, text, (x, y), cv2.FONT_HERSHEY_SIMPLEX, 0.9, 0, 2, cv2.LINE_AA)
            continue
        # Draw upright on a scratch tile, then turn the tile onto the page.
        tile = np.full((60, 190), 255, np.uint8)
        cv2.putText(tile, text, (8, 42), cv2.FONT_HERSHEY_SIMPLEX, 0.85, 0, 2, cv2.LINE_AA)
        turned = cv2.rotate(tile, cv2.ROTATE_90_COUNTERCLOCKWISE)
        h, w = turned.shape
        img[y : y + h, x : x + w] = turned
    return img


def named(items: list[TextItem]) -> dict[str, TextItem]:
    return {i.room_name: i for i in items if i.kind == "room" and i.room_name}


def overlaps(a: BBox, b: BBox) -> bool:
    return (
        a.x < b.x + b.width
        and b.x < a.x + a.width
        and a.y < b.y + b.height
        and b.y < a.y + a.height
    )


class TestLabelsPrintedUpThePage:
    """Tesseract reads along rows, so a vertical label needs the page turned."""

    def test_a_vertical_label_is_missed_without_the_turned_passes(self) -> None:
        upright_only = dataclasses.replace(DEFAULTS, ocr_read_rotated=False)
        found = named(extract_text(page([("KITCHEN", (40, 30), True)]), upright_only))
        assert "KITCHEN" not in found

    def test_a_vertical_label_is_read_when_the_page_is_turned(self) -> None:
        found = named(extract_text(page([("KITCHEN", (40, 30), True)]), DEFAULTS))
        assert "KITCHEN" in found
        assert found["KITCHEN"].rotated

    def test_a_turned_reading_is_placed_where_the_text_actually_is(self) -> None:
        """The box has to be mapped back out of the turned frame.

        Getting this wrong is worse than missing the label, because the name
        then lands on whichever room happens to sit at the mirrored position.
        """
        item = named(extract_text(page([("KITCHEN", (40, 30), True)]), DEFAULTS))["KITCHEN"]
        drawn = BBox(x=40, y=30, width=60, height=190)
        assert overlaps(item.bbox, drawn), f"{item.bbox} is nowhere near {drawn}"

    def test_both_turn_directions_are_tried(self) -> None:
        """A label may read up the page or down it; neither is standard."""
        img = page([("KITCHEN", (40, 30), True)])
        found = named(extract_text(cv2.rotate(img, cv2.ROTATE_180), DEFAULTS))
        assert "KITCHEN" in found

    def test_an_upright_reading_is_never_overruled_by_a_turned_one(self) -> None:
        upright = named(extract_text(page([("KITCHEN", (60, 300), False)]), DEFAULTS))
        assert "KITCHEN" in upright
        assert not upright["KITCHEN"].rotated

    def test_an_unconfident_turned_reading_is_thrown_away(self) -> None:
        """Turning the page drags every horizontal label onto its side, and the
        nonsense that comes back can still fuzzy-match a room name. On one test
        plan the disclaimer footer matched POWDER. Confidence is what separates
        them: real vertical labels score 0.92 and up."""
        img = page([("KITCHEN", (40, 30), True)])
        strict = dataclasses.replace(DEFAULTS, ocr_rotated_min_confidence=0.999)
        assert "KITCHEN" not in named(extract_text(img, strict))
        # ...and it is only the confidence bar keeping it out.
        loose = dataclasses.replace(DEFAULTS, ocr_rotated_min_confidence=0.5)
        assert "KITCHEN" in named(extract_text(img, loose))


class TestNamesThePlanUses:
    """The dictionary is what turns read text into a room name."""

    @pytest.mark.parametrize(
        "written, expected",
        [
            ("BIR", "BIR"),
            ("B.I.R.", "BIR"),
            ("WIR", "WIR"),
            ("PANTRY", "PTY"),
            ("LAUNDRY", "L'DRY"),
            ("TOILET", "WC"),
        ],
    )
    def test_common_abbreviations_are_recognised(self, written: str, expected: str) -> None:
        """BIR sits on nearly every Australian bedroom and was simply absent,
        so the label was read and drawn but the robe stayed an unnamed room."""
        from app.config import ROOM_NAME_DICTIONARY

        assert ROOM_NAME_DICTIONARY.get(written) == expected


class TestDeclutteringTheLabels:
    """Plans draw over their own labels; the strokes they draw with are thinner."""

    def label_crossed_by_an_arc(self) -> np.ndarray:
        img = np.full((200, 320), 255, np.uint8)
        cv2.putText(img, "WIR", (60, 120), cv2.FONT_HERSHEY_SIMPLEX, 1.6, 0, 4, cv2.LINE_AA)
        # A door swing sweeping straight through the lettering. Drawn as a
        # hairline, which is how plans draw them and why this works: the
        # glyph strokes are heavier than the annotation crossing them.
        cv2.ellipse(img, (250, 40), (150, 120), 0, 90, 180, 0, 1)
        return img

    def test_a_label_with_a_door_arc_through_it_is_still_read(self) -> None:
        img = self.label_crossed_by_an_arc()
        found = named(extract_text(img, DEFAULTS, thickness_px=8.0))
        assert "WIR" in found

    def test_without_the_declutter_pass_it_is_lost(self) -> None:
        img = self.label_crossed_by_an_arc()
        plain = dataclasses.replace(DEFAULTS, ocr_declutter=False, ocr_read_rotated=False)
        assert "WIR" not in named(extract_text(img, plain, thickness_px=8.0))
