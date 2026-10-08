/**
 * The electrical engine's data model: the floor plan it designs from, the
 * project brief, fixtures and the electrical design, as immutable records that
 * follow the JSON Schemas in {@code contracts/} at the repository root.
 *
 * <p>An open module: every engine module (layout, circuits, cabling...) reads
 * and writes these types, so they are shared rather than hidden. The model has
 * no Spring dependencies and no behaviour beyond checking its own invariants:
 * a record that exists is valid against its schema. {@link com.hypex.electriplan.model.ModelJson}
 * is the one way to read and write it as JSON; a floor plan from outside comes in
 * through {@link com.hypex.electriplan.model.plan.FloorPlanReader}, which also
 * checks it against the schema and refuses versions this build does not read.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Electrical model",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.model;
