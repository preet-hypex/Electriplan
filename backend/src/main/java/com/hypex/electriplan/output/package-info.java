/**
 * What a design is turned into: the PDF drawing set, schedules, the bill of
 * materials and DXF. Empty until epic E15.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Outputs",
        allowedDependencies = {"model", "design", "catalogue", "switchboard"})
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.output;
