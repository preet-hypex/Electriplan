package com.hypex.electriplan.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.hypex.electriplan.model.common.AustralianState;
import org.junit.jupiter.api.Test;

/** The pack the API ships: it loads, and it claims nothing it has not earned. */
class AuResidentialPackTest {

    static final RulePack PACK = RulePackLoader.load(RulePackSource.classpath("au-residential"));

    @Test
    void loads() {
        assertThat(PACK.id()).isEqualTo("au-residential");
        assertThat(PACK.nationalRules()).isNotEmpty();
        assertThat(PACK.standards()).extracting(RulePack.Standard::code).contains("AS/NZS 3000");
        assertThat(PACK.notices()).isEmpty();
    }

    @Test
    void isADraftWithNothingVerifiedOrSignedOffYet() {
        // Until licensed copies of the standards are in hand (E0-S8) and a
        // Victorian electrician signs off (E16-S4). When that happens, this
        // test is where the pack's status is meant to change.
        assertThat(PACK.status()).isEqualTo(RulePack.Status.DRAFT);
        assertThat(PACK.nationalRules()).noneMatch(Rule::verified);
        assertThat(PACK.supportedStates()).isEmpty();
        assertThat(PACK.states()).contains(AustralianState.VIC);
    }

    @Test
    void hasMandatoryAndPolicyRulesButNoRegulatoryOnesNationally() {
        List<Tier> tiers = PACK.nationalRules().stream().map(Rule::tier).distinct().toList();
        assertThat(tiers).contains(Tier.MANDATORY, Tier.POLICY).doesNotContain(Tier.REGULATORY);
        assertThat(PACK.nationalRules()).allSatisfy(rule -> assertThat(rule.cite()).isNotBlank());
    }

    @Test
    void policyRulesAreNamedAsSuch() {
        assertThat(PACK.nationalRules()).filteredOn(r -> r.tier() == Tier.POLICY)
                .allSatisfy(rule -> assertThat(rule.id()).startsWith("policy."));
    }

    @Test
    void victoriasRulesBuildWhenUnsignedStatesAreAllowed() {
        RuleSet vic = new RuleBook(PACK, true).rulesFor(AustralianState.VIC);
        assertThat(vic.signedOff()).isFalse();
        assertThat(vic.ref().id()).isEqualTo("au-residential");
        assertThat(vic.ref().state()).isEqualTo(AustralianState.VIC);
        assertThat(vic.rule("policy.switch.height").number("value_mm")).isEqualTo(1100);
        assertThat(vic.rule("wet.gpo.zone2").parameters().get("applies_to")).isEqualTo(List.of("gpo-single", "gpo-double", "switch"));
    }
}
