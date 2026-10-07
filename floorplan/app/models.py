"""The FloorPlan wire format.

Mirrors ``frontend/src/model/types.ts`` exactly. Fields are camelCase on the
wire and snake_case in Python; pydantic's alias generator does the translation
so neither side has to compromise.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

FLOORPLAN_VERSION = 1

Source = Literal["vision", "ocr", "geometry", "manual"]


class Wire(BaseModel):
    """Base: camelCase aliases, populated by either name."""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
    )


class Point(Wire):
    x: float
    y: float


class Wall(Wire):
    id: str
    start: Point
    end: Point
    thickness: float
    confidence: float | None = None
    source: Source | None = None


class Room(Wire):
    id: str
    name: str = ""
    polygon: list[Point]
    label_position: Point
    colour: str | None = None
    confidence: float | None = None
    source: Source | None = None


class Door(Wire):
    id: str
    wall_id: str
    position: float
    width: float
    #: How the door opens. Only a swinging door has a hinge and a swing; the
    #: two fields below are ignored for a slider or a garage door.
    style: Literal["swing", "sliding", "garage"] = "swing"
    #: Which jamb the hinge is on, in the wall's own start-to-end direction.
    hinge_at_start: bool = True
    #: Which side the leaf opens to: +90 is the wall's left-hand normal.
    swing: float | None = None
    confidence: float | None = None
    source: Source | None = None


class Window(Wire):
    id: str
    wall_id: str
    position: float
    width: float
    confidence: float | None = None
    source: Source | None = None


class Opening(Wire):
    """A gap in a wall that is neither clearly a door nor clearly a window.

    A cased opening, a garage door drawn dashed, or a doorway whose symbol was
    too faint to read. Kept in the model rather than discarded so the editor
    can show it and let the user say what it is.
    """

    id: str
    wall_id: str
    position: float
    width: float
    confidence: float | None = None
    source: Source | None = None


class Label(Wire):
    id: str
    text: str
    position: Point
    type: Literal["room", "dimension", "other"] = "other"
    room_id: str | None = None
    confidence: float | None = None
    source: Source | None = None


class Dimension(Wire):
    id: str
    start: Point
    end: Point
    value: float
    unit: Literal["mm", "m"] = "mm"
    confidence: float | None = None
    source: Source | None = None


class Rect(Wire):
    x: float
    y: float
    width: float
    height: float


class PlanSource(Wire):
    image_url: str
    image_width: int
    image_height: int
    plan_region: Rect
    mm_per_px: float
    scale_confidence: float
    scale_method: Literal["ocr-dimensions", "wall-thickness", "manual", "fallback"]


class AnalysisStep(Wire):
    name: str
    ok: bool
    detail: str


class AnalysisReport(Wire):
    steps: list[AnalysisStep] = Field(default_factory=list)
    wall_count: int = 0
    room_count: int = 0
    label_count: int = 0
    dimension_count: int = 0
    warnings: list[str] = Field(default_factory=list)


class FloorPlan(Wire):
    version: int = FLOORPLAN_VERSION
    units: Literal["mm"] = "mm"

    walls: list[Wall] = Field(default_factory=list)
    rooms: list[Room] = Field(default_factory=list)
    doors: list[Door] = Field(default_factory=list)
    windows: list[Window] = Field(default_factory=list)
    openings: list[Opening] = Field(default_factory=list)
    labels: list[Label] = Field(default_factory=list)
    dimensions: list[Dimension] = Field(default_factory=list)

    source: PlanSource | None = None
    analysis: AnalysisReport | None = None


class CalibrateRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")

    pixels: float = Field(gt=0, description="Measured length in source pixels")
    millimetres: float = Field(gt=0, description="What that length really is")
    current_mm_per_px: float | None = Field(default=None, gt=0)


class CalibrateResponse(BaseModel):
    mm_per_px: float
    #: How much the plan must be rescaled: new scale ÷ old scale.
    factor: float
    confidence: float


class ExportRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")

    format: Literal["json"] = "json"
    plan: FloorPlan
