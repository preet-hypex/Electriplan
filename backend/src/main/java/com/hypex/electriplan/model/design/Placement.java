package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Where a point is: on a wall face ({@link WallPlacement}) or on the ceiling
 * ({@link CeilingPlacement}). In JSON the {@code type} property says which.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = WallPlacement.class, name = "wall"),
        @JsonSubTypes.Type(value = CeilingPlacement.class, name = "ceiling")
})
public sealed interface Placement permits WallPlacement, CeilingPlacement {
}
