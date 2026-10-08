/**
 * The electrical geometry kernel on JTS: walls and rooms as polygons, offsets,
 * clearances, positions along a wall, distances to fixtures.
 *
 * <p>An open module: every module that places or checks points measures with
 * it, so its types are shared rather than hidden. It is pure geometry and
 * knows nothing of electrical rules. Empty until epic E2.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Electrical geometry",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        allowedDependencies = "model")
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.geometry;
