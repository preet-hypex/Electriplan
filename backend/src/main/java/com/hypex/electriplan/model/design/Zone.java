package com.hypex.electriplan.model.design;

import java.util.List;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.Point;
import com.hypex.electriplan.model.units.Millimetres;

import lombok.Builder;
import lombok.Singular;
import lombok.With;

/** A keep-out volume from a fixture: its outline on plan, its height from the floor, and the clause it comes from. */
@Builder(toBuilder = true)
@With
public record Zone(
        String id,
        ZoneKind kind,
        String fixtureId,
        @Singular("corner") List<Point> polygon,
        Millimetres floorToHeight,
        String rule) {

    public Zone {
        Checks.id(id, "id");
        Checks.required(kind, "kind");
        Checks.id(fixtureId, "fixtureId");
        polygon = Checks.sized(polygon, 3, Integer.MAX_VALUE, "polygon");
        Checks.required(floorToHeight, "floorToHeight");
        Checks.text(rule, "rule");
    }
}
