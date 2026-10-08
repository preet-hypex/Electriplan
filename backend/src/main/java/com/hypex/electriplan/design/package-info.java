/**
 * Designing: the orchestrator that runs the engine's stages in order over a
 * floor plan and brief, and, later, the design REST API, design persistence and
 * the {@code DesignCompleted} event (documents/electrical-engine-plan.md §6.4,
 * epics E0 and E14).
 *
 * <p>What other modules use is in this package: {@link com.hypex.electriplan.design.DesignStage},
 * the contract every layout, loads, circuits, cabling and switchboard stage
 * implements; the {@link com.hypex.electriplan.design.DesignContext} a stage
 * sees; and {@link com.hypex.electriplan.design.DesignOrchestrator} with its
 * {@link com.hypex.electriplan.design.DesignInput}. All are plain Java with no
 * Spring and no I/O, so the same input always gives the same design.
 *
 * <p>The allowed dependencies grow as the stage modules gain stages; for now
 * the module needs only the model.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Design", allowedDependencies = "model")
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.design;
