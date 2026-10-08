/**
 * Roles, electrician review and sign-off of a design, and the acceptance
 * metrics the reviews feed. Empty until E14-S7 and epic E16.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Electrician review",
        allowedDependencies = {"model", "design"})
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.review;
