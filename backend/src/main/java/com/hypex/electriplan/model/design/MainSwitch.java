package com.hypex.electriplan.model.design;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Amperes;

import lombok.Builder;
import lombok.With;

/** The switchboard's main switch: rating and poles (1 for single-phase, 3 for three-phase). */
@Builder(toBuilder = true)
@With
public record MainSwitch(Amperes ratingA, Integer poles) {

    public MainSwitch {
        Checks.positive(Checks.required(ratingA, "ratingA").value(), "ratingA");
        Checks.oneOf(poles, "poles", 1, 3);
    }
}
