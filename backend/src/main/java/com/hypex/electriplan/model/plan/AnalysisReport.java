package com.hypex.electriplan.model.plan;

import java.util.List;

import com.hypex.electriplan.model.Checks;

import lombok.Builder;
import lombok.Singular;
import lombok.With;

/** What the analyser did and found: each step and whether it worked, the counts, and warnings. Diagnostic only; nothing designs from it. */
@Builder(toBuilder = true)
@With
public record AnalysisReport(
        @Singular List<Step> steps,
        Integer wallCount,
        Integer roomCount,
        Integer labelCount,
        Integer dimensionCount,
        @Singular List<String> warnings) {

    public AnalysisReport {
        steps = Checks.list(steps, "steps");
        Checks.between(wallCount, 0, Integer.MAX_VALUE, "wallCount");
        Checks.between(roomCount, 0, Integer.MAX_VALUE, "roomCount");
        Checks.between(labelCount, 0, Integer.MAX_VALUE, "labelCount");
        Checks.between(dimensionCount, 0, Integer.MAX_VALUE, "dimensionCount");
        warnings = Checks.list(warnings, "warnings");
    }

    /** One step of the analysis, and whether it worked. */
    public record Step(String name, Boolean ok, String detail) {

        public Step {
            if (Checks.required(name, "steps[].name").isEmpty()) {
                throw new IllegalArgumentException("steps[].name must not be empty");
            }
            Checks.required(ok, "steps[].ok");
            Checks.required(detail, "steps[].detail");
        }
    }
}
