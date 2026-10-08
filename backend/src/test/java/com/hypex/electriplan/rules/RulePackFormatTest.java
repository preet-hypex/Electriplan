package com.hypex.electriplan.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** What a rule pack must look like: everything the loader refuses, each problem naming its file. */
class RulePackFormatTest {

    private static List<String> problems(ThrowingCallable load) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(load);
        assertThat(thrown).isInstanceOf(InvalidRulePackException.class);
        return ((InvalidRulePackException) thrown).problems();
    }

    private static List<String> problems(Packs packs) {
        return problems(packs::load);
    }

    private static Packs withRule(String rule) {
        return Packs.pack().national("[base.yaml, extra.yaml]").file("extra.yaml", "rules:\n" + rule);
    }

    @Test
    void aWellFormedPackLoads() {
        RulePack pack = Packs.pack().state("VIC", Packs.NO_RULES).load();

        assertThat(pack.id()).isEqualTo("test-pack");
        assertThat(pack.version()).isEqualTo("2026.1-test");
        assertThat(pack.status()).isEqualTo(RulePack.Status.DRAFT);
        assertThat(pack.standards()).extracting(RulePack.Standard::reference).containsExactly("AS/NZS 3000:2018+A3");
        assertThat(pack.nationalRules()).extracting(Rule::id)
                .containsExactly("policy.gpo.corner-clearance", "policy.switch.height", "wet.zone.height");
        Rule height = pack.nationalRules().get(2);
        assertThat(height.tier()).isEqualTo(Tier.MANDATORY);
        assertThat(height.kind()).isEqualTo(RuleKind.VALUE);
        assertThat(height.cite()).isEqualTo("AS/NZS 3000:2018 cl. 6.2.2");
        assertThat(height.parameters()).containsExactly(java.util.Map.entry("value_mm", 2250));
        assertThat(height.verified()).isFalse();
        assertThat(height.source()).isEqualTo("test-pack/base.yaml");
        assertThat(pack.nationalRules().get(0).parameters().get("applies_to")).isEqualTo(List.of("gpo-double"));
    }

    @ParameterizedTest(name = "a rule without {0} is refused")
    @ValueSource(strings = {"id", "tier", "kind", "cite"})
    void everyRuleNeedsAnIdTierKindAndCitation(String missing) {
        List<String> fields = new java.util.ArrayList<>(List.of(
                "id: wet.extra", "tier: mandatory", "kind: value", "cite: \"AS/NZS 3000:2018 cl. 1\"", "value_mm: 1"));
        fields.removeIf(f -> f.startsWith(missing + ":"));
        StringBuilder rule = new StringBuilder("  - " + fields.get(0) + "\n");
        fields.subList(1, fields.size()).forEach(f -> rule.append("    ").append(f).append("\n"));

        assertThat(problems(withRule(rule.toString())))
                .singleElement().asString()
                .startsWith("extra.yaml: ")
                .contains("required property '" + missing + "' not found");
    }

    @Test
    void theProblemNamesTheRuleWhenItHasAnId() {
        assertThat(problems(withRule("""
                  - id: wet.extra
                    tier: mandatory
                    kind: value
                    value_mm: 1
                """))).containsExactly("extra.yaml: $.rules[0]: required property 'cite' not found (rule wet.extra)");
    }

    @Test
    void anEmptyCitationIsNoCitation() {
        assertThat(problems(withRule("""
                  - id: wet.extra
                    tier: mandatory
                    kind: value
                    cite: "   "
                """))).singleElement().asString().contains("cite").contains("(rule wet.extra)");
    }

    @Test
    void tiersAndKindsAreTheKnownOnes() {
        assertThat(problems(withRule("""
                  - id: wet.extra
                    tier: optional
                    kind: guess
                    cite: x
                """))).hasSize(2).allSatisfy(p -> assertThat(p).contains("does not have a value in the enumeration"));
    }

    @Test
    void idsAreDottedLowercase() {
        assertThat(problems(withRule("""
                  - id: WetExtra
                    tier: mandatory
                    kind: value
                    cite: x
                """))).singleElement().asString().contains("$.rules[0].id");
    }

    @Test
    void aParameterMayNotBeNull() {
        assertThat(problems(withRule("""
                  - id: wet.extra
                    tier: mandatory
                    kind: value
                    value_mm: ~
                    cite: x
                """))).singleElement().asString().contains("value_mm");
    }

    @Test
    void idsAreUniqueAcrossTheNationalFiles() {
        assertThat(problems(withRule("""
                  - id: wet.zone.height
                    tier: mandatory
                    kind: value
                    value_mm: 2300
                    cite: x
                """))).containsExactly("extra.yaml: rule wet.zone.height is also in test-pack/base.yaml");
    }

    @Test
    void andWithinAStateFile() {
        String twice = """
                rules:
                  - id: vic.extra
                    tier: regulatory
                    kind: value
                    cite: x
                  - id: vic.extra
                    tier: regulatory
                    kind: value
                    cite: y
                """;
        assertThat(problems(Packs.pack().state("VIC", twice))).containsExactly("state/VIC.yaml: rule vic.extra is there twice");
    }

    @Test
    void regulatoryRulesBelongInStateFiles() {
        assertThat(problems(withRule("""
                  - id: supply.limit
                    tier: regulatory
                    kind: limit
                    cite: x
                """))).containsExactly("extra.yaml: rule supply.limit is regulatory: state variations belong in a state file");
    }

    @Test
    void onlyAStateFileOverrides() {
        assertThat(problems(withRule("""
                  - id: wet.other
                    tier: mandatory
                    kind: value
                    cite: x
                    override: true
                """))).containsExactly("extra.yaml: rule wet.other says override: only a state file can replace a national rule");
    }

    @Test
    void yamlMistakesAreReportedWithTheirFile() {
        assertThat(problems(withRule("  - id: [unclosed\n"))).singleElement().asString().startsWith("extra.yaml: not valid YAML");
        assertThat(problems(withRule("""
                  - id: wet.extra
                    id: wet.again
                    tier: mandatory
                    kind: value
                    cite: x
                """))).singleElement().asString().startsWith("extra.yaml: not valid YAML").contains("Duplicate field 'id'");
        assertThat(problems(Packs.pack().national("[base.yaml, extra.yaml]").file("extra.yaml", "")))
                .containsExactly("extra.yaml: empty");
    }

    @Test
    void aListedFileMustExist() {
        assertThat(problems(Packs.pack().national("[base.yaml, lighting.yaml]"))).containsExactly("lighting.yaml: missing");
        assertThat(problems(Packs.pack().state("VIC", Packs.NO_RULES).without("state/VIC.yaml")))
                .containsExactly("state/VIC.yaml: missing");
    }

    @Test
    void everyProblemInEveryFileIsReportedTogether() {
        List<String> problems = problems(Packs.pack().national("[base.yaml, a.yaml, b.yaml]")
                .file("a.yaml", "rules:\n  - id: a.one\n    tier: mandatory\n    kind: value\n")
                .file("b.yaml", "rules:\n  - id: b.one\n    tier: policy\n    kind: value\n")
                .state("VIC", "rules:\n  - id: vic.one\n    kind: value\n    cite: x\n"));

        assertThat(problems).hasSize(3);
        assertThat(problems).anySatisfy(p -> assertThat(p).startsWith("a.yaml:").contains("cite"));
        assertThat(problems).anySatisfy(p -> assertThat(p).startsWith("b.yaml:").contains("cite"));
        assertThat(problems).anySatisfy(p -> assertThat(p).startsWith("state/VIC.yaml:").contains("tier"));
        assertThatThrownBy(() -> Packs.pack().national("[base.yaml, a.yaml]")
                .file("a.yaml", "rules:\n  - id: a.one\n    tier: mandatory\n    kind: value\n").load())
                .hasMessageStartingWith("Rule pack test-pack is not valid:\n  - a.yaml:");
    }

    @Test
    void metaIsCheckedToo() {
        assertThat(problems(Packs.pack().meta("id: test-pack\nversion: 1\n")))
                .anySatisfy(p -> assertThat(p).startsWith("meta.yaml:").contains("status"));
        assertThat(problems(Packs.pack().status("final"))).singleElement().asString().startsWith("meta.yaml:").contains("status");
        assertThat(problems(() -> RulePackLoader.load(RulePackSource.of("test-pack", java.util.Map.of()))))
                .containsExactly("meta.yaml: missing");
    }

    @Test
    void theMetaIdIsThePacksName() {
        assertThat(problems(() -> RulePackLoader.load(RulePackSource.of("other-pack", java.util.Map.of(
                "meta.yaml", Packs.pack().metaYaml(), "base.yaml", "rules: []\n")))))
                .containsExactly("meta.yaml: id is test-pack but the pack is other-pack");
    }

    @Test
    void stateCodesAndFileNamesAreChecked() {
        assertThat(problems(Packs.pack().meta(Packs.pack().metaYaml().replace("states: {}", "states:\n  XX:\n    file: state/XX.yaml"))))
                .singleElement().asString().startsWith("meta.yaml:");
        assertThat(problems(Packs.pack().meta(Packs.pack().metaYaml().replace("states: {}", "states:\n  VIC:\n    file: state/NSW.yaml"))
                .file("state/NSW.yaml", Packs.NO_RULES)))
                .containsExactly("meta.yaml: VIC's rules must be in state/VIC.yaml, not state/NSW.yaml");
    }

    @Test
    void aSignOffNeedsARealDate() {
        assertThat(problems(Packs.pack().state("VIC", Packs.NO_RULES).meta(Packs.pack().signedState("VIC", Packs.NO_RULES)
                .metaYaml().replace("2026-11-02", "2026-13-45"))))
                .containsExactly("meta.yaml: the sign-off of state/VIC.yaml has no real date: 2026-13-45");
    }
}
