package com.hypex.electriplan.rules;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.model.design.RulePackRef;

/**
 * A loaded, checked rule pack: its national rules and its state files.
 * Made by {@link RulePackLoader}, which refuses a pack with any problem, so
 * everything here already holds: ids unique, tiers in the right files, state
 * overrides that replace a national rule and never make a mandatory one
 * optional.
 *
 * <p>The states the engine designs are the pack's <b>supported states</b>: the
 * states whose file is signed off and unchanged since. Nothing here, or
 * anywhere in the engine, names a state.
 */
public final class RulePack {

    public enum Status { DRAFT, RELEASE }

    /** A standard the pack encodes, at the edition it was encoded from. */
    public record Standard(String code, String edition, boolean verified) {

        /** As a design records it: {@code AS/NZS 3000:2018+A3}. */
        public String reference() {
            return code + ":" + edition;
        }
    }

    private final String id;
    private final String version;
    private final Status status;
    private final List<Standard> standards;
    private final List<Rule> national;
    private final Map<AustralianState, StateRules> states;
    private final List<String> notices;

    RulePack(String id, String version, Status status, List<Standard> standards, List<Rule> national,
             Map<AustralianState, StateRules> states, List<String> notices) {
        this.id = id;
        this.version = version;
        this.status = status;
        this.standards = List.copyOf(standards);
        this.national = national.stream().sorted(Comparator.comparing(Rule::id)).toList();
        EnumMap<AustralianState, StateRules> byState = new EnumMap<>(AustralianState.class);
        byState.putAll(states);
        this.states = Collections.unmodifiableMap(byState);
        this.notices = List.copyOf(notices);
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    public Status status() {
        return status;
    }

    public List<Standard> standards() {
        return standards;
    }

    /** The rules every state starts from, by id. */
    public List<Rule> nationalRules() {
        return national;
    }

    /** States the pack has a rule file for, signed off or not. */
    public Set<AustralianState> states() {
        return states.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(states.keySet()));
    }

    /** States whose rule file is signed off and unchanged since: the states the engine designs. */
    public Set<AustralianState> supportedStates() {
        EnumSet<AustralianState> supported = EnumSet.noneOf(AustralianState.class);
        states.values().stream().filter(StateRules::signedOff).forEach(s -> supported.add(s.state()));
        return Collections.unmodifiableSet(supported);
    }

    public Optional<StateRules> state(AustralianState state) {
        return Optional.ofNullable(states.get(state));
    }

    /** Things to know that do not stop the pack loading, such as a sign-off voided by a later change. */
    public List<String> notices() {
        return notices;
    }

    /** What a design made with this pack in that state records. */
    public RulePackRef ref(AustralianState state) {
        return RulePackRef.builder().id(id).version(version).state(state)
                .standards(standards.stream().map(Standard::reference).toList()).build();
    }

    /**
     * The rules for a state: the national rules, with the state's own added
     * and its overrides in place of the national rules they replace. Whether
     * the state may be designed is {@link RuleBook}'s question; this answers
     * for any state the pack has a file for.
     *
     * @throws UnsupportedStateException when the pack has no file for the state
     */
    public RuleSet rulesFor(AustralianState state) {
        StateRules stateRules = states.get(state);
        if (stateRules == null) {
            throw new UnsupportedStateException(state, "the " + id + " rule pack has no rules for it");
        }
        Map<String, Rule> merged = new TreeMap<>();
        national.forEach(rule -> merged.put(rule.id(), rule));
        stateRules.rules().forEach(rule -> merged.put(rule.id(), rule));
        return new RuleSet(ref(state), stateRules.signedOff(), List.copyOf(merged.values()));
    }
}
