package com.hypex.electriplan.model.plan;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A floor plan of a version this build does not read: made by a newer analyser
 * or editor, or an old one this build no longer understands. Refused as a whole
 * rather than read in part.
 */
public class UnsupportedFloorPlanVersionException extends InvalidFloorPlanException {

    private final long version;
    private final Set<Integer> supportedVersions;

    public UnsupportedFloorPlanVersionException(long version, Set<Integer> supportedVersions) {
        super(List.of("version " + version + " is not supported; this build reads version "
                + String.join(", ", new TreeSet<>(supportedVersions).stream().map(String::valueOf).toList())));
        this.version = version;
        this.supportedVersions = Set.copyOf(supportedVersions);
    }

    /** The version the plan says it is. */
    public long version() {
        return version;
    }

    /** The versions this build reads. */
    public Set<Integer> supportedVersions() {
        return supportedVersions;
    }
}
