"""FastAPI application entry point.

    uvicorn app.main:app --reload --port 8082

The floor-plan analyser: one of the services behind the web app, next to the
Java API. Every route lives under /api/floorplan, so the web app's proxy (Vite in
development, nginx in Docker) can send that prefix here and the rest of /api to
the Java API. Requests are same-origin; the CORS middleware below only exists for
the case where someone runs the built frontend from a different origin.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .api.routes import router

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")

app = FastAPI(
    title="Floor-plan analyser",
    version="0.1.0",
    description=(
        "Turns a floor-plan image into an editable FloorPlan model: wall detection, "
        "room reconstruction, OCR labels and scale estimation."
    ),
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5180", "http://localhost:4180"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)


@app.get("/")
def root() -> dict[str, str]:
    return {
        "service": "floorplan-editor-api",
        "docs": "/docs",
        "analyse": "POST /api/floorplan/analyse",
    }
