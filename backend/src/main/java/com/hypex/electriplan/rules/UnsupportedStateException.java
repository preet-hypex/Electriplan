package com.hypex.electriplan.rules;

import com.hypex.electriplan.model.common.AustralianState;

/** The rule pack has no signed-off rules for this state, so the engine does not design there. */
public class UnsupportedStateException extends RuntimeException {

    private final AustralianState state;

    public UnsupportedStateException(AustralianState state, String why) {
        super("Electriplan does not design " + state + " houses yet" + (why.isEmpty() ? "" : ": " + why));
        this.state = state;
    }

    public AustralianState state() {
        return state;
    }
}
