package com.hypex.electriplan.model.design;

import com.hypex.electriplan.model.Checks;

/** Whether a surge protective device is fitted, and why. */
public record SurgeProtection(Boolean fitted, String reason) {

    public SurgeProtection {
        Checks.required(fitted, "fitted");
        Checks.text(reason, "reason");
    }
}
