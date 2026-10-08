package com.hypex.electriplan.rules;

import java.util.List;

/** A rule pack that cannot be used, with every problem found, each naming its file. */
public class InvalidRulePackException extends RuntimeException {

    private final transient List<String> problems;

    public InvalidRulePackException(String pack, List<String> problems) {
        super("Rule pack " + pack + " is not valid:\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
