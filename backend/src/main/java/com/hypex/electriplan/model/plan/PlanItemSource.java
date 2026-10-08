package com.hypex.electriplan.model.plan;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How an item came to be in the plan: found in the image, read from its text,
 * inferred from other geometry, or drawn or corrected by a person.
 */
public enum PlanItemSource {
    @JsonProperty("vision") VISION,
    @JsonProperty("ocr") OCR,
    @JsonProperty("geometry") GEOMETRY,
    @JsonProperty("manual") MANUAL
}
