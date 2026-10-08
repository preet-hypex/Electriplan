/**
 * The switchboard: its schedule of protective devices, main switch, surge
 * protection and pole count, and the single-line diagram. Empty until
 * epic E12.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Switchboard",
        allowedDependencies = {"model", "rules", "catalogue", "loads", "circuits"})
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.switchboard;
