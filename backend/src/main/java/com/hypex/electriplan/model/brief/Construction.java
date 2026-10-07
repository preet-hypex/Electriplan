package com.hypex.electriplan.model.brief;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Millimetres;

import lombok.Builder;
import lombok.With;

/** How the house is built, as far as cable routing and lighting care. */
@Builder(toBuilder = true)
@With
public record Construction(
        Integer storeys,
        Millimetres defaultCeilingHeight,
        Boolean ceilingInsulated,
        Boolean roofSpaceAccessible,
        Boolean slab) {

    public Construction {
        Checks.between(storeys, 1, 4, "storeys");
        Checks.between(Checks.required(defaultCeilingHeight, "defaultCeilingHeight").value(), 2100, 6000, "defaultCeilingHeight");
        Checks.required(ceilingInsulated, "ceilingInsulated");
        Checks.required(roofSpaceAccessible, "roofSpaceAccessible");
        Checks.required(slab, "slab");
    }
}
