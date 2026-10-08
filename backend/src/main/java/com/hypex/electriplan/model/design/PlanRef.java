package com.hypex.electriplan.model.design;

import java.util.regex.Pattern;

import com.hypex.electriplan.model.Checks;

import lombok.Builder;
import lombok.With;

/** The floor plan a design was made from, so a design made from an older plan can be detected. */
@Builder(toBuilder = true)
@With
public record PlanRef(String planHash, Integer planVersion) {

    public static final Pattern HASH = Pattern.compile("^sha256:[0-9a-f]{64}$");

    public PlanRef {
        Checks.matches(planHash, HASH, "planHash");
        Checks.between(planVersion, 1, Integer.MAX_VALUE, "planVersion");
    }
}
