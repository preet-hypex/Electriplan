package com.hypex.electriplan.model.plan;

import com.fasterxml.jackson.annotation.JsonProperty;

/** How the plan's scale was found. {@link #FALLBACK} means it was guessed: calibrate before sizing cables. */
public enum ScaleMethod {
    @JsonProperty("ocr-dimensions") OCR_DIMENSIONS,
    @JsonProperty("wall-thickness") WALL_THICKNESS,
    @JsonProperty("manual") MANUAL,
    @JsonProperty("fallback") FALLBACK
}
