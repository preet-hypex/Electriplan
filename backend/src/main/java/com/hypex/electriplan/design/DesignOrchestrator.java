package com.hypex.electriplan.design;

import java.util.List;
import java.util.Objects;

import com.hypex.electriplan.model.design.ElectricalDesign;

/**
 * Runs the engine: starts from an empty design for the input's plan and rule
 * pack, then runs each stage in the given order, each seeing the design the
 * one before it returned (§6.4). With no stages it returns the empty design,
 * which is already valid against {@code contracts/electrical-design.schema.json}.
 *
 * <p>Plain Java and synchronous: the order is fixed when the orchestrator is
 * made, and a run has no effect beyond its result, so the same input always
 * gives the same design. Thread-safe as long as its stages are.
 */
public final class DesignOrchestrator {

    private final List<DesignStage> stages;

    public DesignOrchestrator(List<? extends DesignStage> stages) {
        Objects.requireNonNull(stages, "stages");
        stages.forEach(stage -> Objects.requireNonNull(stage, "stages[]"));
        this.stages = List.copyOf(stages);
    }

    /** The stages, in the order they run. */
    public List<DesignStage> stages() {
        return stages;
    }

    /** Designs from the input; throws if a stage breaks the stage contract. */
    public ElectricalDesign design(DesignInput input) {
        ElectricalDesign start = ElectricalDesign.empty(
                PlanFingerprint.planRef(input.plan(), input.planRevision()), input.rulePack());
        DesignContext context = new DesignContext(input, start);
        for (DesignStage stage : stages) {
            context = context.withDesign(checked(stage, context.design(), stage.apply(context)));
        }
        return context.design();
    }

    private static ElectricalDesign checked(DesignStage stage, ElectricalDesign before, ElectricalDesign after) {
        if (after == null) {
            throw new IllegalStateException("Stage " + stage.name() + " returned no design");
        }
        if (!after.planRef().equals(before.planRef()) || !after.rulePack().equals(before.rulePack())) {
            throw new IllegalStateException("Stage " + stage.name()
                    + " changed which plan or rule pack the design is for");
        }
        return after;
    }
}
