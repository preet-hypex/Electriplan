package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

import com.hypex.electriplan.model.Checks;

import lombok.Builder;
import lombok.Singular;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * An electrical design for one house plan: points, wet-area zones, circuits,
 * switchboard, maximum demand, and what breaks a rule or needs an
 * electrician's decision. Follows {@code contracts/electrical-design.schema.json}.
 *
 * <p>Immutable. Engine stages add to a design with the {@code with...} methods
 * or {@link #toBuilder()}; they never change one in place.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record ElectricalDesign(
        Integer version,
        PlanRef planRef,
        RulePackRef rulePack,
        @Singular List<DesignPoint> points,
        @Singular List<Zone> zones,
        @Singular List<Circuit> circuits,
        @Nullable Switchboard switchboard,
        @Nullable MaxDemand maxDemand,
        @Singular List<Violation> violations,
        @Singular("decisionRequired") List<DecisionRequired> decisionsRequired) {

    public static final int VERSION = 1;

    public ElectricalDesign {
        Checks.oneOf(version, "version", VERSION);
        Checks.required(planRef, "planRef");
        Checks.required(rulePack, "rulePack");
        points = Checks.list(points, "points");
        zones = Checks.list(zones, "zones");
        circuits = Checks.list(circuits, "circuits");
        violations = Checks.list(violations, "violations");
        decisionsRequired = Checks.list(decisionsRequired, "decisionsRequired");
    }

    /** A builder with the current version already set. */
    public static ElectricalDesignBuilder builder() {
        return new ElectricalDesignBuilder().version(VERSION);
    }

    /** A new design with nothing in it yet: where the engine starts. */
    public static ElectricalDesign empty(PlanRef planRef, RulePackRef rulePack) {
        return builder().planRef(planRef).rulePack(rulePack).build();
    }

    /** Whether the design breaks a mandatory rule, so cannot be committed. */
    public boolean hasErrors() {
        return violations.stream().anyMatch(v -> v.severity() == Severity.ERROR);
    }
}
