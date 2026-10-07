"""Where uploaded images live while the editor references them.

Deliberately a directory on disk keyed by a random id — no database, no
accounts. The FloorPlan JSON the user saves is self-contained apart from the
``source.imageUrl`` pointer, and the editor works fine if that image is gone.
"""

from __future__ import annotations

import os
import uuid
from dataclasses import dataclass
from pathlib import Path

DEFAULT_DIR = Path(__file__).resolve().parent.parent / ".uploads"

SUFFIX_BY_CONTENT_TYPE = {
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/png": ".png",
}


class UnsupportedImageType(ValueError):
    pass


@dataclass
class StoredImage:
    image_id: str
    path: Path

    @property
    def url(self) -> str:
        return f"/api/floorplan/images/{self.image_id}"


class ImageStore:
    def __init__(self, directory: Path | None = None) -> None:
        self.directory = Path(
            directory or os.environ.get("FLOORPLAN_UPLOAD_DIR") or DEFAULT_DIR
        )
        self.directory.mkdir(parents=True, exist_ok=True)

    def save(self, data: bytes, content_type: str | None, filename: str | None) -> StoredImage:
        suffix = SUFFIX_BY_CONTENT_TYPE.get((content_type or "").lower())
        if suffix is None and filename:
            ext = Path(filename).suffix.lower()
            suffix = {".jpg": ".jpg", ".jpeg": ".jpg", ".png": ".png"}.get(ext)
        if suffix is None:
            raise UnsupportedImageType(
                f"Unsupported image type {content_type or filename or 'unknown'!r}. "
                "Upload a JPG or PNG."
            )

        image_id = f"{uuid.uuid4().hex}{suffix}"
        path = self.directory / image_id
        # Re-create the directory rather than assume it: it is a scratch
        # directory, and anything from a clean script to a temp sweeper may
        # have removed it since this store was constructed.
        self.directory.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return StoredImage(image_id=image_id, path=path)

    def path_for(self, image_id: str) -> Path | None:
        """Resolve an id to a file, refusing anything that escapes the store."""
        candidate = (self.directory / image_id).resolve()
        try:
            candidate.relative_to(self.directory.resolve())
        except ValueError:
            return None
        return candidate if candidate.is_file() else None
