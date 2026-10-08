package com.hypex.electriplan.rules;

import java.time.LocalDate;

import com.hypex.electriplan.model.Checks;

/**
 * A licensed electrician from the state signed its rule file off.
 *
 * @param sha256 of the file as it was signed: a later change voids the sign-off
 */
public record SignOff(String by, String licence, LocalDate date, String sha256) {

    public SignOff {
        Checks.text(by, "signOff.by");
        Checks.text(licence, "signOff.licence");
        Checks.required(date, "signOff.date");
        Checks.text(sha256, "signOff.sha256");
    }
}
