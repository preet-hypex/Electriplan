package com.hypex.electriplan.model.design;

import java.util.List;
import java.util.regex.Pattern;

import com.hypex.electriplan.model.Checks;

import lombok.Builder;
import lombok.Singular;
import lombok.With;

/** A rule the design breaks, the items involved, and what is wrong. */
@Builder(toBuilder = true)
@With
public record Violation(String ruleId, Severity severity, @Singular List<String> itemIds, String message) {

    /** Dotted rule ids, as in the rule packs: wet.gpo.zone2. */
    public static final Pattern RULE_ID = Pattern.compile("^[a-z0-9]+(\\.[a-z0-9-]+)*$");

    public Violation {
        Checks.matches(ruleId, RULE_ID, "ruleId");
        Checks.required(severity, "severity");
        itemIds = Checks.ids(itemIds, "itemIds");
        Checks.text(message, "message");
    }
}
