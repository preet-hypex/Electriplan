package com.hypex.electriplan.design;

import com.hypex.electriplan.model.design.ElectricalDesign;

/**
 * One step of the engine: lighting layout, switch placement, circuit grouping,
 * cable sizing... Every stage has this shape, so each can be tested, replaced
 * and re-run on its own, and {@link DesignOrchestrator} runs them in a fixed
 * order.
 *
 * <p>A stage is a pure function: it reads the context, returns the design with
 * its additions, and has no other effect. The same context must always give
 * the same design. Stages only add to the design (the {@code with...} methods
 * and {@code toBuilder()} of {@link ElectricalDesign} make a new one); they
 * never remove or move an item a person placed, and never change which plan
 * or rule pack the design is for.
 */
@FunctionalInterface
public interface DesignStage {

    /** The design so far with this stage's additions. Never null. */
    ElectricalDesign apply(DesignContext context);

    /** How the stage is named in errors and, later, stage reports. */
    default String name() {
        return getClass().getSimpleName();
    }
}
