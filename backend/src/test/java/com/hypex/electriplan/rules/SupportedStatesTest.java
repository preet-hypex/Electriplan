package com.hypex.electriplan.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hypex.electriplan.model.common.AustralianState;
import org.junit.jupiter.api.Test;

/** The states the engine designs are the states with a signed-off file, and nothing else decides it. */
class SupportedStatesTest {

    private static final String NSW_RULES = """
            rules:
              - id: supply.single-phase-limit
                tier: regulatory
                kind: limit
                at_most_a: 100
                cite: "NSW Service & Installation Rules"
            """;

    @Test
    void aStateWithoutASignOffIsNotSupported() {
        RulePack pack = Packs.pack().state("VIC", Packs.NO_RULES).load();
        assertThat(pack.states()).containsExactly(AustralianState.VIC);
        assertThat(pack.supportedStates()).isEmpty();
        assertThat(pack.state(AustralianState.VIC).orElseThrow().signedOff()).isFalse();
    }

    @Test
    void aSignedOffStateIsSupported() {
        RulePack pack = Packs.pack().signedState("VIC", Packs.NO_RULES).load();
        assertThat(pack.supportedStates()).containsExactly(AustralianState.VIC);
        StateRules vic = pack.state(AustralianState.VIC).orElseThrow();
        assertThat(vic.signOff().by()).isEqualTo("Pat Sparks");
        assertThat(vic.signOff().licence()).isEqualTo("REC-12345");
        assertThat(vic.signOff().date()).isEqualTo("2026-11-02");
        assertThat(pack.notices()).isEmpty();
    }

    @Test
    void nothingInTheEngineFavoursVictoria() {
        RulePack pack = Packs.pack().state("VIC", Packs.NO_RULES).signedState("NSW", NSW_RULES).load();
        assertThat(pack.supportedStates()).containsExactly(AustralianState.NSW);

        RuleBook book = new RuleBook(pack, false);
        assertThat(book.designableStates()).containsExactly(AustralianState.NSW);
        assertThat(book.rulesFor(AustralianState.NSW).rule("supply.single-phase-limit").number("at_most_a")).isEqualTo(100);
        assertThatThrownBy(() -> book.rulesFor(AustralianState.VIC))
                .isInstanceOf(UnsupportedStateException.class)
                .hasMessage("Electriplan does not design VIC houses yet: its rules are not signed off by a licensed electrician");
    }

    @Test
    void changingASignedFileVoidsItsSignOff() {
        String signed = NSW_RULES;
        String edited = NSW_RULES.replace("100", "80");
        RulePack pack = Packs.pack().signedState("NSW", signed).file("state/NSW.yaml", edited).load();

        assertThat(pack.supportedStates()).isEmpty();
        assertThat(pack.state(AustralianState.NSW).orElseThrow().signOff()).isNotNull();
        assertThat(pack.notices()).containsExactly("state/NSW.yaml has changed since Pat Sparks signed it off on 2026-11-02:"
                + " NSW is not supported until it is signed off again");
        assertThatThrownBy(() -> new RuleBook(pack, false).rulesFor(AustralianState.NSW))
                .hasMessage("Electriplan does not design NSW houses yet: its rules have changed since they were signed off");
    }

    @Test
    void aStateWithNoFileIsNeverDesigned() {
        RuleBook book = new RuleBook(Packs.pack().state("VIC", Packs.NO_RULES).load(), true);
        assertThat(book.designs(AustralianState.QLD)).isFalse();
        assertThatThrownBy(() -> book.rulesFor(AustralianState.QLD))
                .isInstanceOf(UnsupportedStateException.class)
                .hasMessage("Electriplan does not design QLD houses yet: the test-pack rule pack has no rules for it")
                .extracting(e -> ((UnsupportedStateException) e).state()).isEqualTo(AustralianState.QLD);
    }

    @Test
    void unsignedStatesAreDesignedOnlyWhenAllowed() {
        RulePack pack = Packs.pack().state("VIC", Packs.NO_RULES).signedState("NSW", NSW_RULES).load();

        assertThat(new RuleBook(pack, false).designableStates()).containsExactly(AustralianState.NSW);
        RuleBook development = new RuleBook(pack, true);
        assertThat(development.designableStates()).containsExactlyInAnyOrder(AustralianState.NSW, AustralianState.VIC);

        RuleSet vic = development.rulesFor(AustralianState.VIC);
        assertThat(vic.signedOff()).isFalse();
        assertThat(development.rulesFor(AustralianState.NSW).signedOff()).isTrue();
    }

    @Test
    void theRuleSetSaysWhichRulesItIs() {
        RuleSet nsw = new RuleBook(Packs.pack().signedState("NSW", NSW_RULES).load(), false).rulesFor(AustralianState.NSW);
        assertThat(nsw.ref().id()).isEqualTo("test-pack");
        assertThat(nsw.ref().version()).isEqualTo("2026.1-test");
        assertThat(nsw.ref().state()).isEqualTo(AustralianState.NSW);
        assertThat(nsw.ref().standards()).containsExactly("AS/NZS 3000:2018+A3");
    }

    @Test
    void aPackWithNoStatesDesignsNothing() {
        RuleBook book = new RuleBook(Packs.pack().load(), true);
        assertThat(book.designableStates()).isEmpty();
        for (AustralianState state : AustralianState.values()) {
            assertThat(book.designs(state)).isFalse();
        }
    }
}
