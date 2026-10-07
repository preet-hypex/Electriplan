package com.hypex.electriplan.model.brief;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Metres;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/** The electricity supply: phases (v1 designs single-phase), voltage, and the consumer mains run if known. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Supply(Integer phases, Integer nominalVoltage, @Nullable Metres consumerMainsLengthM) {

    public static final int NOMINAL_VOLTAGE = 230;

    public Supply {
        Checks.oneOf(phases, "phases", 1, 3);
        Checks.oneOf(nominalVoltage, "nominalVoltage", NOMINAL_VOLTAGE);
        if (consumerMainsLengthM != null) {
            Checks.positive(consumerMainsLengthM.value(), "consumerMainsLengthM");
        }
    }
}
