package com.hypex.electriplan.rules;

import java.util.List;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.AustralianState;

import org.jspecify.annotations.Nullable;

/**
 * A state's rule file in a pack.
 *
 * @param signOff as recorded in meta.yaml, if any
 * @param signedOff the sign-off is there and the file has not changed since:
 *        only then does the pack support the state
 */
public record StateRules(AustralianState state, String file, List<Rule> rules, @Nullable SignOff signOff, boolean signedOff) {

    public StateRules {
        Checks.required(state, "state");
        Checks.text(file, "file");
        rules = Checks.list(rules, "rules");
        if (signedOff && signOff == null) {
            throw new IllegalArgumentException(state + " cannot be signed off without a sign-off");
        }
    }
}
