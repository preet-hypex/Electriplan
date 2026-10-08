package com.hypex.electriplan.design;

import java.util.List;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.brief.ProjectBrief;
import com.hypex.electriplan.model.design.RulePackRef;
import com.hypex.electriplan.model.fixture.Fixture;
import com.hypex.electriplan.model.plan.FloorPlan;
import com.hypex.electriplan.rules.RuleSet;

/**
 * Everything a design is made from: the floor plan and which saved revision of
 * it this is, the project brief, the fixtures, and the rule pack to design
 * with.
 *
 * @param planRevision the plan's revision number as saved
 *        ({@code floor_plan_version.version_no}), recorded in the design's
 *        {@code planRef.planVersion}. Not the FloorPlan schema version.
 * @param rules the rules the design is made with, for the brief's state
 *        (from {@link com.hypex.electriplan.rules.RuleBook#rulesFor})
 */
public record DesignInput(
        FloorPlan plan,
        int planRevision,
        ProjectBrief brief,
        List<Fixture> fixtures,
        RuleSet rules) {

    public DesignInput {
        Checks.required(plan, "plan");
        Checks.between(planRevision, 1, Integer.MAX_VALUE, "planRevision");
        Checks.required(brief, "brief");
        fixtures = Checks.list(fixtures, "fixtures");
        Checks.required(rules, "rules");
        if (rules.ref().state() != brief.state()) {
            throw new IllegalArgumentException("The rules are for " + rules.ref().state()
                    + " but the brief is for " + brief.state());
        }
    }

    /** Which rules these are, as the design records them. */
    public RulePackRef rulePack() {
        return rules.ref();
    }
}
