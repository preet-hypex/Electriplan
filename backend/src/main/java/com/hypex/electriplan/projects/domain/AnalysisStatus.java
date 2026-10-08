package com.hypex.electriplan.projects.domain;

/** Where an analysis run is: electriplan.analysis_run.status. */
public enum AnalysisStatus {
    QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED;

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
