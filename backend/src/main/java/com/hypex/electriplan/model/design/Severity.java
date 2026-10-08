package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonProperty;

/** How bad a rule violation is: an error (a mandatory rule) blocks committing the design. */
public enum Severity {
    @JsonProperty("error") ERROR,
    @JsonProperty("warning") WARNING
}
