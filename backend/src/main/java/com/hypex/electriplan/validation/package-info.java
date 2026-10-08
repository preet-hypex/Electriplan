/**
 * Runs every rule against any design, whether the engine or a person made it,
 * and produces the compliance report. It does not depend on the modules that
 * place points, so it checks their work rather than trusting it (§6.8 of
 * documents/electrical-engine-plan.md). Empty until epic E13.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Validation",
        allowedDependencies = {"model", "geometry", "zones", "rules", "catalogue"})
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.validation;
