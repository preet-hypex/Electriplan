/**
 * Point loads and maximum demand (AS/NZS 3000 Appendix C), including the check
 * that the house fits a single-phase supply. Empty until epic E8.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Loads and maximum demand",
        allowedDependencies = {"model", "rules", "catalogue"})
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.loads;
