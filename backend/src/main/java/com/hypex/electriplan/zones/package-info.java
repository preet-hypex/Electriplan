/**
 * Wet-area zones (AS/NZS 3000 Section 6) around baths, showers and basins, and
 * the permission matrix of what may go in each zone. Empty until epic E3.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Wet-area zones",
        allowedDependencies = {"model", "geometry", "rules"})
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.zones;
