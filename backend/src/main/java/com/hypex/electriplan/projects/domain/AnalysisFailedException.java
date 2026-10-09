package com.hypex.electriplan.projects.domain;

/**
 * The analyser could not make a plan from the image, or could not be reached.
 *
 * @see #unavailable() whether it was not answering (rather than refusing the image)
 */
public class AnalysisFailedException extends RuntimeException {

    private final boolean unavailable;

    public AnalysisFailedException(String message, boolean unavailable, Throwable cause) {
        super(message, cause);
        this.unavailable = unavailable;
    }

    public AnalysisFailedException(String message) {
        super(message);
        this.unavailable = false;
    }

    public boolean unavailable() {
        return unavailable;
    }
}
