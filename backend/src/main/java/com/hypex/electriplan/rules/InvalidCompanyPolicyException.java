package com.hypex.electriplan.rules;

import java.util.List;

/** A company policy that changes what it may not, with every problem found. */
public class InvalidCompanyPolicyException extends RuntimeException {

    private final transient List<String> problems;

    public InvalidCompanyPolicyException(String source, List<String> problems) {
        super(source + " cannot be applied:\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
