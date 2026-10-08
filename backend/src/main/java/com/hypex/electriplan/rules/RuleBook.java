package com.hypex.electriplan.rules;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.hypex.electriplan.model.common.AustralianState;

/**
 * The rule pack the engine designs with, and which states it designs.
 *
 * <p>A state is designed when its rule file is signed off. With
 * {@code allowUnsignedStates} (development and tests only, never production)
 * a state with an unsigned file is designed too, and its {@link RuleSet} says
 * {@code signedOff = false}. A state with no file is never designed.
 */
public final class RuleBook {

    private final RulePack pack;
    private final boolean allowUnsignedStates;

    public RuleBook(RulePack pack, boolean allowUnsignedStates) {
        this.pack = pack;
        this.allowUnsignedStates = allowUnsignedStates;
    }

    public RulePack pack() {
        return pack;
    }

    public boolean allowsUnsignedStates() {
        return allowUnsignedStates;
    }

    /** The states the engine will design now. */
    public Set<AustralianState> designableStates() {
        Set<AustralianState> states = allowUnsignedStates ? pack.states() : pack.supportedStates();
        return states.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(states));
    }

    public boolean designs(AustralianState state) {
        return designableStates().contains(state);
    }

    /**
     * The rules to design a house in this state with.
     *
     * @throws UnsupportedStateException "Electriplan does not design NSW houses yet", with why
     */
    public RuleSet rulesFor(AustralianState state) {
        StateRules file = pack.state(state).orElseThrow(() ->
                new UnsupportedStateException(state, "the " + pack.id() + " rule pack has no rules for it"));
        if (!file.signedOff() && !allowUnsignedStates) {
            throw new UnsupportedStateException(state, file.signOff() == null
                    ? "its rules are not signed off by a licensed electrician"
                    : "its rules have changed since they were signed off");
        }
        return pack.rulesFor(state);
    }
}
