package com.hypex.electriplan.design;

import java.util.List;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.brief.ProjectBrief;
import com.hypex.electriplan.model.design.RulePackRef;
import com.hypex.electriplan.model.fixture.Fixture;
import com.hypex.electriplan.model.plan.FloorPlan;

/**
 * Everything a design is made from: the floor plan and which saved revision of
 * it this is, the project brief, the fixtures, and the rule pack to design
 * with.
 *
 * @param planRevision the plan's revision number as saved
 *        ({@code floor_plan_version.version_no}), recorded in the design's
 *        {@code planRef.planVersion}. Not the FloorPlan schema version.
 * @param rulePack the rule pack release and state the design is made with;
 *        its state must be the brief's state.
 */
public record DesignInput(
        FloorPlan plan,
        int planRevision,
        ProjectBrief brief,
        List<Fixture> fixtures,
        RulePackRef rulePack) {

    public DesignInput {
        Checks.required(plan, "plan");
        Checks.between(planRevision, 1, Integer.MAX_VALUE, "planRevision");
        Checks.required(brief, "brief");
        fixtures = Checks.list(fixtures, "fixtures");
        Checks.required(rulePack, "rulePack");
        if (rulePack.state() != brief.state()) {
            throw new IllegalArgumentException("rulePack.state is " + rulePack.state()
                    + " but the brief is for " + brief.state());
        }
    }
}
