package com.hypex.electriplan.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import java.util.Map;

import com.hypex.electriplan.model.common.AustralianState;
import org.junit.jupiter.api.Test;

/** A company changes the values of policy rules, and never anything mandatory. */
class CompanyPolicyTest {

    static final String ACME = "company: Acme Electrical";

    private static RuleSet vic() {
        return Packs.pack().state("VIC", """
                rules:
                  - id: supply.single-phase-limit
                    tier: regulatory
                    kind: limit
                    at_most_a: 63
                    cite: "Service & Installation Rules"
                """).load().rulesFor(AustralianState.VIC);
    }

    private static CompanyPolicy policy(CompanyPolicy.Change... changes) {
        return new CompanyPolicy(ACME, List.of(changes));
    }

    private static CompanyPolicy.Change change(String id, Map<String, Object> values) {
        return new CompanyPolicy.Change(id, values);
    }

    private static List<String> refused(CompanyPolicy policy) {
        return catchThrowableOfType(InvalidCompanyPolicyException.class, () -> vic().withCompanyPolicy(policy)).problems();
    }

    @Test
    void aCompanyChangesAPolicyValue() {
        RuleSet rules = vic().withCompanyPolicy(policy(change("policy.switch.height", Map.of("value_mm", 1200))));

        Rule height = rules.rule("policy.switch.height");
        assertThat(height.number("value_mm")).isEqualTo(1200);
        assertThat(height.tier()).isEqualTo(Tier.POLICY);
        assertThat(height.cite()).isEqualTo("Company standard");
        assertThat(height.source()).isEqualTo(ACME);
        assertThat(rules.rule("wet.zone.height")).isEqualTo(vic().rule("wet.zone.height"));
        assertThat(rules.ref()).isEqualTo(vic().ref());
    }

    @Test
    void onlyTheValuesNamedChange() {
        RuleSet rules = vic().withCompanyPolicy(policy(change("policy.gpo.corner-clearance", Map.of("distance_mm", 450))));

        Rule clearance = rules.rule("policy.gpo.corner-clearance");
        assertThat(clearance.number("distance_mm")).isEqualTo(450);
        assertThat(clearance.parameters().get("applies_to")).isEqualTo(List.of("gpo-double"));
    }

    @Test
    void theRulesItWasAppliedToAreUnchanged() {
        RuleSet before = vic();
        before.withCompanyPolicy(policy(change("policy.switch.height", Map.of("value_mm", 1200))));
        assertThat(before.rule("policy.switch.height").number("value_mm")).isEqualTo(1100);
    }

    @Test
    void neverAMandatoryRule() {
        assertThat(refused(policy(change("wet.zone.height", Map.of("value_mm", 2000)))))
                .containsExactly("wet.zone.height: a mandatory rule; company policy can change policy rules only, never mandatory ones");
    }

    @Test
    void norARegulatoryOne() {
        assertThat(refused(policy(change("supply.single-phase-limit", Map.of("at_most_a", 80)))))
                .containsExactly("supply.single-phase-limit: a regulatory rule; company policy can change policy rules only,"
                        + " never regulatory ones");
    }

    @Test
    void norARulesTierKindOrCitation() {
        assertThat(refused(policy(change("policy.switch.height", Map.of("tier", "mandatory", "cite", "Ours")))))
                .containsExactlyInAnyOrder(
                        "policy.switch.height: 'tier' cannot be changed, only the rule's values",
                        "policy.switch.height: 'cite' cannot be changed, only the rule's values");
    }

    @Test
    void onlyValuesTheRuleHasToValuesOfTheSameType() {
        assertThat(refused(policy(change("policy.switch.height", Map.of("height_mm", 1200)))))
                .containsExactly("policy.switch.height: has no value 'height_mm' (it has value_mm)");
        assertThat(refused(policy(change("policy.switch.height", Map.of("value_mm", "high")))))
                .containsExactly("policy.switch.height: 'value_mm' must be a number, not text");
    }

    @Test
    void onlyRulesThePackHas() {
        assertThat(refused(policy(change("policy.switch.colour", Map.of("value", "white")))))
                .containsExactly("policy.switch.colour: no such rule in test-pack 2026.1-test for VIC");
    }

    @Test
    void eachRuleOnce() {
        assertThat(refused(policy(change("policy.switch.height", Map.of("value_mm", 1200)),
                change("policy.switch.height", Map.of("value_mm", 1000)))))
                .containsExactly("policy.switch.height: changed twice");
    }

    @Test
    void everyProblemIsReportedAndNothingIsApplied() {
        CompanyPolicy bad = policy(change("wet.zone.height", Map.of("value_mm", 1)),
                change("policy.switch.height", Map.of("value_mm", 1200)),
                change("policy.nope", Map.of("x", 1)));
        assertThat(refused(bad)).hasSize(2);
        assertThatThrownBy(() -> vic().withCompanyPolicy(bad)).hasMessageStartingWith(ACME + " cannot be applied:");
    }

    @Test
    void aStatesOverrideOfAPolicyRuleCanStillBeChanged() {
        RuleSet qld = Packs.pack().state("QLD", """
                rules:
                  - id: policy.switch.height
                    tier: policy
                    kind: value
                    value_mm: 1000
                    cite: "State guidance"
                    override: true
                """).load().rulesFor(AustralianState.QLD);

        assertThat(qld.withCompanyPolicy(policy(change("policy.switch.height", Map.of("value_mm", 1150))))
                .rule("policy.switch.height").number("value_mm")).isEqualTo(1150);
    }

    @Test
    void aChangeHasValues() {
        assertThatThrownBy(() -> change("policy.switch.height", Map.of())).hasMessageContaining("no values");
    }
}
