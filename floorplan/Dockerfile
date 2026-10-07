# syntax=docker/dockerfile:1

FROM python:3.11-slim

# Tesseract is a real binary, not a Python package: without it the analyser still
# finds walls and rooms, but every room comes back unnamed.
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng curl \
 && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 --create-home floorplan \
 # Owned by the service user, so a new volume mounted here inherits that.
 && mkdir -p /data/uploads && chown floorplan:floorplan /data/uploads
WORKDIR /home/floorplan

COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements.txt

COPY --chown=floorplan:floorplan app ./app

USER floorplan
ENV FLOORPLAN_UPLOAD_DIR=/data/uploads
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -fsS http://localhost:8000/api/floorplan/health >/dev/null || exit 1

ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
