/**
 * The floor plan the engine designs from: walls, rooms, doors, windows,
 * openings, labels and dimensions, in millimetres. Follows
 * {@code contracts/floor-plan.schema.json}, the contract the analyser and the
 * editor are checked against too. The engine only reads a plan, never changes
 * it; {@link com.hypex.electriplan.model.plan.FloorPlanReader} is how one comes in.
 */
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.model.plan;
