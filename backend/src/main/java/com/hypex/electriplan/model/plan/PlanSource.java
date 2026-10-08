package com.hypex.electriplan.model.plan;

import com.hypex.electriplan.model.Checks;

import lombok.Builder;
import lombok.With;

/**
 * The image a plan was analysed from: where the untouched upload is, its size,
 * the drawing region in its pixels, the scale applied to get millimetres, and
 * how sure that scale is. Below a threshold the engine refuses to size cables
 * until the plan is calibrated.
 */
@Builder(toBuilder = true)
@With
public record PlanSource(
        String imageUrl,
        Integer imageWidth,
        Integer imageHeight,
        PlanRegion planRegion,
        Double mmPerPx,
        Double scaleConfidence,
        ScaleMethod scaleMethod) {

    public PlanSource {
        if (Checks.required(imageUrl, "imageUrl").isEmpty()) {
            throw new IllegalArgumentException("imageUrl must not be empty");
        }
        Checks.between(imageWidth, 1, Integer.MAX_VALUE, "imageWidth");
        Checks.between(imageHeight, 1, Integer.MAX_VALUE, "imageHeight");
        Checks.required(planRegion, "planRegion");
        Checks.positive(Checks.required(mmPerPx, "mmPerPx"), "mmPerPx");
        Checks.between(Checks.required(scaleConfidence, "scaleConfidence"), 0, 1, "scaleConfidence");
        Checks.required(scaleMethod, "scaleMethod");
    }

    /** The detected drawing region, in pixels of the original image. */
    @Builder(toBuilder = true)
    @With
    public record PlanRegion(Double x, Double y, Double width, Double height) {

        public PlanRegion {
            Checks.nonNegative(Checks.required(x, "planRegion.x"), "planRegion.x");
            Checks.nonNegative(Checks.required(y, "planRegion.y"), "planRegion.y");
            Checks.positive(Checks.required(width, "planRegion.width"), "planRegion.width");
            Checks.positive(Checks.required(height, "planRegion.height"), "planRegion.height");
        }
    }
}
