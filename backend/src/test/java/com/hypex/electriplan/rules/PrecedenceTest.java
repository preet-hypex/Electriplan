package com.hypex.electriplan.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;

import com.hypex.electriplan.model.common.AustralianState;
import org.junit.jupiter.api.Test;

/** State overrides national; a state never makes a mandatory rule optional. */
class PrecedenceTest {

    private static final String TALLER_ZONES = """
            rules:
              - id: wet.zone.height
                tier: mandatory
                kind: value
                value_mm: 2400
                cite: "State regulation 12"
                override: true
            """;

    private static List<String> refused(Packs packs) {
        return catchThrowableOfType(InvalidRulePackException.class, packs::load).problems();
    }

    @Test
    void aStateOverrideReplacesTheNationalRuleInThatStateOnly() {
        RulePack pack = Packs.pack().state("QLD", TALLER_ZONES).state("VIC", Packs.NO_RULES).load();

        Rule qld = pack.rulesFor(AustralianState.QLD).rule("wet.zone.height");
        assertThat(qld.number("value_mm")).isEqualTo(2400);
        assertThat(qld.cite()).isEqualTo("State regulation 12");
        assertThat(qld.source()).isEqualTo("test-pack/state/QLD.yaml");

        Rule vic = pack.rulesFor(AustralianState.VIC).rule("wet.zone.height");
        assertThat(vic.number("value_mm")).isEqualTo(2250);
        assertThat(vic.source()).isEqualTo("test-pack/base.yaml");
        assertThat(pack.nationalRules()).extracting(Rule::source).containsOnly("test-pack/base.yaml");
    }

    @Test
    void aStatesOwnRulesAreAddedForThatStateOnly() {
        RulePack pack = Packs.pack().state("VIC", """
                rules:
                  - id: supply.single-phase-limit
                    tier: regulatory
                    kind: limit
                    at_most_a: 63
                    cite: "Service & Installation Rules"
                """).state("NSW", Packs.NO_RULES).load();

        RuleSet vic = pack.rulesFor(AustralianState.VIC);
        assertThat(vic.rules()).extracting(Rule::id).containsExactly(
                "policy.gpo.corner-clearance", "policy.switch.height", "supply.single-phase-limit", "wet.zone.height");
        assertThat(vic.tier(Tier.REGULATORY)).extracting(Rule::id).containsExactly("supply.single-phase-limit");
        assertThat(pack.rulesFor(AustralianState.NSW).find("supply.single-phase-limit")).isEmpty();
        assertThat(pack.rulesFor(AustralianState.NSW).rules()).hasSize(3);
    }

    @Test
    void aStateRuleWithANationalIdMustSayItOverrides() {
        assertThat(refused(Packs.pack().state("VIC", TALLER_ZONES.replace("    override: true\n", ""))))
                .containsExactly("state/VIC.yaml: rule wet.zone.height has the id of a national rule (test-pack/base.yaml):"
                        + " add 'override: true' to replace it, or give it its own id");
    }

    @Test
    void anOverrideMustReplaceSomething() {
        assertThat(refused(Packs.pack().state("VIC", TALLER_ZONES.replace("wet.zone.height", "wet.zone.heigth"))))
                .containsExactly("state/VIC.yaml: rule wet.zone.heigth says override, but there is no national rule"
                        + " wet.zone.heigth to replace");
    }

    @Test
    void anOverrideKeepsTheKind() {
        assertThat(refused(Packs.pack().state("VIC", TALLER_ZONES.replace("kind: value", "kind: limit"))))
                .containsExactly("state/VIC.yaml: rule wet.zone.height is a limit rule but replaces a value rule");
    }

    @Test
    void aStateNeverMakesAMandatoryRuleOptional() {
        assertThat(refused(Packs.pack().state("VIC", TALLER_ZONES.replace("tier: mandatory", "tier: policy"))))
                .containsExactly("state/VIC.yaml: rule wet.zone.height would make a mandatory rule a policy rule:"
                        + " a state may change a mandatory rule's values, never make it optional");
    }

    @Test
    void butMayMakeItRegulatory() {
        RulePack pack = Packs.pack().state("VIC", TALLER_ZONES.replace("tier: mandatory", "tier: regulatory")).load();
        assertThat(pack.rulesFor(AustralianState.VIC).rule("wet.zone.height").tier()).isEqualTo(Tier.REGULATORY);
    }

    @Test
    void andMayMakeAPolicyRuleMandatory() {
        RulePack pack = Packs.pack().state("QLD", """
                rules:
                  - id: policy.gpo.corner-clearance
                    tier: mandatory
                    kind: clearance
                    applies_to: [gpo-double]
                    distance_mm: 500
                    cite: "State regulation 3"
                    override: true
                """).load();
        Rule rule = pack.rulesFor(AustralianState.QLD).rule("policy.gpo.corner-clearance");
        assertThat(rule.tier()).isEqualTo(Tier.MANDATORY);
        assertThat(rule.number("distance_mm")).isEqualTo(500);
    }

    @Test
    void theSameInputAlwaysGivesTheSameRules() {
        RuleSet first = Packs.pack().state("QLD", TALLER_ZONES).load().rulesFor(AustralianState.QLD);
        RuleSet second = Packs.pack().state("QLD", TALLER_ZONES).load().rulesFor(AustralianState.QLD);
        assertThat(first).isEqualTo(second);
        assertThat(first.rules()).extracting(Rule::id).isSorted();
    }
}
