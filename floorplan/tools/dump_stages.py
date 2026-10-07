"""Write every intermediate of the analysis to PNGs, for eyeballing.

    python -m tools.dump_stages <image> [outdir]

Each stage is written in order, including the individual sub-steps of the
geometry cleanup, which is where a wall set most often quietly goes wrong.
"""

from __future__ import annotations

import sys
from pathlib import Path

import cv2
import numpy as np

from app.config import DEFAULTS as C
from app.config import derive
from app.pipeline import geometry as G
from app.pipeline import openings, preprocess, region, rooms, walls

WIDTH = 1100
OPENING_COLOURS = {
    "door": (60, 170, 60),
    "sliding": (30, 140, 240),
    "garage": (200, 60, 200),
    "window": (60, 60, 225),
    "opening": (150, 150, 150),
}


def main(path: str, outdir: str = "stages") -> None:
    out = Path(outdir)
    out.mkdir(parents=True, exist_ok=True)
    index = [0]

    def save(name: str, img: np.ndarray) -> None:
        index[0] += 1
        height = int(img.shape[0] * WIDTH / img.shape[1])
        scaled = cv2.resize(img, (WIDTH, height), interpolation=cv2.INTER_AREA)
        cv2.imwrite(str(out / f"{index[0]:02d}_{name}.png"), scaled)

    def mask(m: np.ndarray) -> np.ndarray:
        return 255 - m

    raw = preprocess.load_image(Path(path).read_bytes())
    save("original", raw)
    norm = preprocess.normalise(raw, C)
    save("normalised", norm.gray)

    reg = region.detect_region(norm.gray, C)
    boxed = raw.copy()
    cv2.rectangle(boxed, (reg.x, reg.y), (reg.x + reg.width, reg.y + reg.height), (0, 0, 255), 6)
    save("region", boxed)

    gray = reg.crop(norm.gray)
    ink = preprocess.binarise(gray)
    save("ink_otsu", mask(ink))

    rough = derive(C, reg.width, reg.height, 0.0).min_wall_length_px
    thickness = preprocess.estimate_wall_thickness(ink, C, rough)
    d = derive(C, reg.width, reg.height, thickness)
    marks = preprocess.faint_marks(gray, C, int(d.wall_thickness_px * C.faint_mark_block_ratio))
    save("marks_adaptive", mask(marks))
    save("long_runs", mask(preprocess.linear_structures(ink, rough)))

    det = walls.detect_walls(ink, C, d)
    save("thick_strokes", mask(det.thick_mask))

    def draw(segs, extra=(), label="") -> np.ndarray:
        canvas = np.full((gray.shape[0], gray.shape[1], 3), 255, np.uint8)
        for s in segs:
            cv2.line(canvas, (int(s.x1), int(s.y1)), (int(s.x2), int(s.y2)),
                     (40, 40, 40), max(1, int(s.thickness)))
        for s in extra:
            cv2.line(canvas, (int(s.x1), int(s.y1)), (int(s.x2), int(s.y2)),
                     (0, 0, 220), max(2, int(s.thickness)))
        # Mark anything that is not exactly on an axis: it should never happen.
        for s in segs:
            if min(abs(s.x2 - s.x1), abs(s.y2 - s.y1)) > 0.5:
                cv2.circle(canvas, (int(s.midpoint[0]), int(s.midpoint[1])), 14, (0, 140, 255), 3)
        if label:
            cv2.putText(canvas, label, (12, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (120, 120, 120), 2)
        return canvas

    save("traced_raw", draw(det.segments, label=f"{len(det.segments)} raw"))

    # Cleanup, one sub-step at a time.
    step = G.drop_short(det.segments, d.min_wall_length_px)
    save("clean1_drop_short", draw(step, label=f"{len(step)}"))
    step = G.snap_to_axis(step, C)
    save("clean2_snap_to_axis", draw(step, label=f"{len(step)}"))
    step = G.merge_collinear(step, C)
    save("clean3_merge_collinear", draw(step, label=f"{len(step)}"))
    step = G.snap_endpoints(step, C)
    save("clean4_snap_endpoints", draw(step, label=f"{len(step)}"))
    step = G.extend_to_intersections(step, C)
    save("clean5_extend", draw(step, label=f"{len(step)}"))
    step = G.merge_collinear(step, C)
    save("clean6_merge_again", draw(step, label=f"{len(step)}"))
    step = G.align_axes(step, C)
    save("clean7_align_axes", draw(step, label=f"{len(step)}"))
    step = G.deduplicate(step, C)
    save("clean8_deduplicate", draw(step, label=f"{len(step)}"))
    cleaned = G.drop_slivers(step, C, d.min_wall_length_px)
    save("clean9_drop_slivers", draw(cleaned, label=f"{len(cleaned)}"))

    with_stubs = G.readmit_stubs(
        cleaned, det.stubs, C, d.wall_thickness_px, C.max_opening_frac * d.min_dim
    )
    added = [s for s in with_stubs if s not in cleaned]
    save("stubs_readmitted", draw(cleaned, extra=added, label=f"{len(with_stubs)} (+{len(added)})"))

    detail = openings._non_wall_ink(ink, with_stubs, d)
    save("ink_without_walls", mask(detail))

    gapped = draw(with_stubs)
    tally: dict[str, int] = {}
    for gap in openings.find_gaps(with_stubs, C, d, marks):
        if not gap.collinear:
            continue
        kind, _, _ = openings.classify_gap(gap, detail, C, d, marks)
        tally[kind] = tally.get(kind, 0) + 1
        cv2.line(gapped, (int(gap.p1[0]), int(gap.p1[1])), (int(gap.p2[0]), int(gap.p2[1])),
                 OPENING_COLOURS[kind], 6)
    save("openings_classified", gapped)

    resolved = openings.resolve_openings(with_stubs, ink, C, d, marks)
    save("walls_healed", draw(resolved.walls, label=f"{len(resolved.walls)} walls"))

    found = rooms.detect_rooms(resolved.walls, C, d, resolved.closures)
    canvas = draw(resolved.walls)
    overlay = canvas.copy()
    rng = np.random.default_rng(7)
    for room in found.rooms:
        pts = np.array([[int(x), int(y)] for x, y in room.polygon], np.int32)
        cv2.fillPoly(overlay, [pts], tuple(int(v) for v in rng.integers(90, 240, 3)))
    save("rooms", cv2.addWeighted(overlay, 0.45, canvas, 0.55, 0))

    print(f"thickness={d.wall_thickness_px}px  min_wall_length={d.min_wall_length_px}px")
    print(f"raw={len(det.segments)} cleaned={len(cleaned)} +stubs={len(with_stubs)} "
          f"healed={len(resolved.walls)} rooms={len(found.rooms)}")
    print("openings:", tally)
    print(f"wrote {index[0]} images to {out}/")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "stages")
