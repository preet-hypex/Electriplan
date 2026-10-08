"""The HTTP surface: analyse, calibrate, export.

The analyser only analyses. It keeps no files: the caller says where the plan
should point for its image (``image_url``). The Java API stores house uploads
in the company's files and passes their URL; the scratch editor keeps the image
in the browser.
"""

from __future__ import annotations

import json
import logging

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import Response

from .. import models
from ..auth import current_user
from ..config import DEFAULTS, PipelineConfig
from ..pipeline import scale as scale_module
from ..pipeline.orchestrator import AnalysisError, analyse
from ..pipeline.preprocess import ImageLoadError

log = logging.getLogger("floorplan.api")

# Health is public, for container health checks. Everything else needs a
# signed-in Supabase user (see app/auth.py).
public_router = APIRouter(prefix="/api/floorplan")
router = APIRouter(prefix="/api/floorplan", dependencies=[Depends(current_user)])

#: The image types the pipeline reads.
SUPPORTED_TYPES = {"image/jpeg", "image/jpg", "image/png"}
SUPPORTED_SUFFIXES = (".jpg", ".jpeg", ".png")

#: Where a plan points for its image when the caller does not say: the caller's
#: own copy (the scratch editor shows the file it picked).
LOCAL_IMAGE = "local:uploaded-image"

#: Refuse anything larger up front rather than spending a minute on it.
MAX_UPLOAD_BYTES = 25 * 1024 * 1024


@public_router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@router.post(
    "/analyse",
    response_model=models.FloorPlan,
    response_model_by_alias=True,
    # Absent optionals are omitted rather than sent as null: the model treats
    # "no confidence" and "confidence unknown" the same, and a null would have
    # to be special-cased by every consumer.
    response_model_exclude_none=True,
)
async def analyse_endpoint(
    file: UploadFile = File(..., description="Floor-plan image (JPG or PNG)"),
    mm_per_px: float | None = Form(
        default=None, description="Skip scale estimation and use this instead"
    ),
    image_url: str = Form(
        default=LOCAL_IMAGE,
        description="Where the plan should point for its image (source.imageUrl), e.g. the API's /api/files/{id}",
        min_length=1,
        max_length=500,
    ),
) -> models.FloorPlan:
    """Reconstruct an editable FloorPlan from a floor-plan image."""
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="The uploaded file was empty.")
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"Image is {len(data) / 1e6:.1f} MB; the limit is {MAX_UPLOAD_BYTES / 1e6:.0f} MB.",
        )
    if mm_per_px is not None and mm_per_px <= 0:
        raise HTTPException(status_code=400, detail="mm_per_px must be greater than zero.")

    content_type = (file.content_type or "").lower()
    if content_type not in SUPPORTED_TYPES and not (file.filename or "").lower().endswith(SUPPORTED_SUFFIXES):
        raise HTTPException(
            status_code=415,
            detail=f"Unsupported image type {file.content_type or file.filename or 'unknown'!r}. Upload a JPG or PNG.",
        )

    config = PipelineConfig(settings=DEFAULTS, mm_per_px_override=mm_per_px)
    try:
        result = analyse(data, image_url, config)
    except ImageLoadError as exc:
        raise HTTPException(status_code=415, detail=str(exc)) from exc
    except AnalysisError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - last resort
        log.exception("Analysis failed for %s", file.filename)
        raise HTTPException(
            status_code=500,
            detail=f"Analysis failed unexpectedly: {exc}. Try again, or trace the walls manually.",
        ) from exc

    return result.plan


@router.post("/calibrate", response_model=models.CalibrateResponse)
def calibrate_endpoint(request: models.CalibrateRequest) -> models.CalibrateResponse:
    """Convert a known measurement into a scale, and report the correction."""
    try:
        mm_per_px = scale_module.calibrate(request.pixels, request.millimetres)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    factor = mm_per_px / request.current_mm_per_px if request.current_mm_per_px else 1.0
    return models.CalibrateResponse(mm_per_px=mm_per_px, factor=factor, confidence=1.0)


@router.post("/export")
def export_endpoint(request: models.ExportRequest) -> Response:
    """Validate a plan and return it as canonical JSON.

    Only ``json`` for now. Other formats (DXF above all) plug in here without
    the frontend changing anything but the requested format.
    """
    if request.format != "json":
        raise HTTPException(status_code=400, detail=f"Unsupported format {request.format!r}.")

    payload = request.plan.model_dump(by_alias=True, exclude_none=True)
    return Response(
        content=json.dumps(payload, indent=2),
        media_type="application/json",
        headers={"Content-Disposition": 'attachment; filename="floorplan.json"'},
    )
