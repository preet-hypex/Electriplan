package com.hypex.electriplan.model.design;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.SquareMillimetres;

/** The cable from the point of supply to the switchboard. */
public record ConsumerMains(SquareMillimetres csaMm2, String type) {

    public ConsumerMains {
        Checks.positive(Checks.required(csaMm2, "csaMm2").value(), "csaMm2");
        Checks.text(type, "consumerMains.type");
    }
}
