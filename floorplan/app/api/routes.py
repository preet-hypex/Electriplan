"""The HTTP surface: analyse, calibrate, export, plus serving the upload back."""

from __future__ import annotations

import json
import logging

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, Response

from .. import models
from ..config import DEFAULTS, PipelineConfig
from ..pipeline import scale as scale_module
from ..pipeline.orchestrator import AnalysisError, analyse
from ..pipeline.preprocess import ImageLoadError
from ..storage import ImageStore, UnsupportedImageType

log = logging.getLogger("floorplan.api")

router = APIRouter(prefix="/api/floorplan")
store = ImageStore()

#: Refuse anything larger up front rather than spending a minute on it.
MAX_UPLOAD_BYTES = 25 * 1024 * 1024


@router.get("/health")
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

    try:
        stored = store.save(data, file.content_type, file.filename)
    except UnsupportedImageType as exc:
        raise HTTPException(status_code=415, detail=str(exc)) from exc

    config = PipelineConfig(settings=DEFAULTS, mm_per_px_override=mm_per_px)
    try:
        result = analyse(data, stored.url, config)
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


@router.get("/images/{image_id}")
def image_endpoint(image_id: str) -> FileResponse:
    """Serve an uploaded image back so the editor can show it underneath."""
    path = store.path_for(image_id)
    if path is None:
        raise HTTPException(status_code=404, detail="No such image.")
    return FileResponse(path)
