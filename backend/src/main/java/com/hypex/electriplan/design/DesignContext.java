package com.hypex.electriplan.design;

import java.util.List;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.brief.ProjectBrief;
import com.hypex.electriplan.model.design.ElectricalDesign;
import com.hypex.electriplan.model.design.RulePackRef;
import com.hypex.electriplan.model.fixture.Fixture;
import com.hypex.electriplan.model.plan.FloorPlan;
import com.hypex.electriplan.rules.RuleSet;

/**
 * What a {@link DesignStage} sees: what the design is made from, and the design
 * as the stages before it left it. Immutable, like everything it holds.
 *
 * <p>{@link #rules()} are the rules to design with: national, the state's,
 * and any company policy values, by id.
 */
public record DesignContext(DesignInput input, ElectricalDesign design) {

    public DesignContext {
        Checks.required(input, "input");
        Checks.required(design, "design");
    }

    public FloorPlan plan() {
        return input.plan();
    }

    public ProjectBrief brief() {
        return input.brief();
    }

    public List<Fixture> fixtures() {
        return input.fixtures();
    }

    public RuleSet rules() {
        return input.rules();
    }

    public RulePackRef rulePack() {
        return input.rulePack();
    }

    /** The same input with a later design: what the next stage sees. */
    public DesignContext withDesign(ElectricalDesign next) {
        return new DesignContext(input, next);
    }
}
