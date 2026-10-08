/**
 * Placing points: lighting, other ceiling devices, switches, socket outlets
 * and appliance points, each in its own sub-package and each contributing a
 * {@code DesignStage} to the orchestrator in {@code design}. Empty until
 * epics E4 to E7.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Point layout",
        allowedDependencies = {"model", "geometry", "zones", "rules", "catalogue"})
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.layout;
