package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Amperes;

import lombok.Builder;
import lombok.Singular;
import lombok.With;
import org.jspecify.annotations.Nullable;

/** Maximum demand: the method, amperes per phase (one value for single-phase), and the consumer mains it needs. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record MaxDemand(
        String method,
        @Singular("phaseA") List<Amperes> perPhaseA,
        @Nullable ConsumerMains consumerMains) {

    public MaxDemand {
        Checks.text(method, "method");
        perPhaseA = Checks.sized(perPhaseA, 1, 3, "perPhaseA");
    }
}
