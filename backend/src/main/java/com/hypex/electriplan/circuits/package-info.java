/**
 * Grouping points into circuits, assigning RCD and RCBO protection, and naming
 * the circuits. Empty until epic E9.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Circuits",
        allowedDependencies = {"model", "rules", "loads"})
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.circuits;
