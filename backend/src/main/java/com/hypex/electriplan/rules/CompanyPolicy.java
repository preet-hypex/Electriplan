package com.hypex.electriplan.rules;

import java.util.List;
import java.util.Map;

import com.hypex.electriplan.model.Checks;

/**
 * A company's own values for policy rules (switch height, outlets per bedroom),
 * applied on top of a rule set with {@link RuleSet#withCompanyPolicy}. Stored
 * per company (E0-S7); only policy rules can be changed.
 *
 * @param source who this policy is, for the rules it changes (e.g. "company: Acme Electrical")
 */
public record CompanyPolicy(String source, List<Change> changes) {

    public CompanyPolicy {
        Checks.text(source, "source");
        changes = Checks.list(changes, "changes");
    }

    /** New values for one rule's parameters. */
    public record Change(String id, Map<String, Object> values) {

        public Change {
            Checks.text(id, "id");
            values = Parameters.copy(values, id);
            if (values.isEmpty()) {
                throw new IllegalArgumentException(id + ": no values to change");
            }
        }
    }
}
