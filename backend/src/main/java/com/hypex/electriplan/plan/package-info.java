/**
 * Floor plan intake: taking in a FloorPlan with its fixtures, checking it,
 * inferring room types, and the readiness check that says whether a plan is
 * complete enough to design from (documents/electrical-engine-plan.md §4.2).
 * Empty until epic E1.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Plan intake", allowedDependencies = "model")
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.plan;
