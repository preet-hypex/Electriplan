package com.hypex.electriplan.model.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Millimetres;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * A position on a wall face, so it moves with the wall: the wall, the distance
 * from its start, the face, and optionally the height above the finished floor.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record WallAnchor(String wallId, Millimetres position, Side side, @Nullable Millimetres height) {

    public WallAnchor {
        Checks.id(wallId, "wallId");
        Checks.required(position, "position");
        Checks.required(side, "side");
    }
}
