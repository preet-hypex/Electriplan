package com.hypex.electriplan.model.design;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.Side;
import com.hypex.electriplan.model.units.Millimetres;

import lombok.Builder;
import lombok.With;

/** On a wall face: the wall, distance from its start, face, and height to the item's centre. */
@Builder(toBuilder = true)
@With
public record WallPlacement(String wallId, Millimetres position, Side side, Millimetres height) implements Placement {

    public WallPlacement {
        Checks.id(wallId, "wallId");
        Checks.required(position, "position");
        Checks.required(side, "side");
        Checks.required(height, "height");
    }
}
