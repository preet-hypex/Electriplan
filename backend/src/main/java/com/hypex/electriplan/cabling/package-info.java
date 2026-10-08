/**
 * Cable routes and lengths, cable and protection sizing, voltage drop and
 * fault-loop impedance (AS/NZS 3000 and AS/NZS 3008.1.1). Empty until epics
 * E10 and E11.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Cabling",
        allowedDependencies = {"model", "geometry", "rules", "catalogue", "circuits"})
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.cabling;
