package com.hypex.electriplan.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.model.design.RulePackRef;
import org.junit.jupiter.api.Test;

class RuleSetTest {

    static final RulePackRef REF = RulePackRef.builder().id("test-pack").version("1").state(AustralianState.VIC).build();

    static Rule rule(String id) {
        return Rule.builder().id(id).tier(Tier.POLICY).kind(RuleKind.VALUE).cite("x").parameter("value_mm", 1).source("test").build();
    }

    @Test
    void rulesAreSortedByIdAndUnique() {
        RuleSet set = new RuleSet(REF, true, List.of(rule("b.two"), rule("a.one")));
        assertThat(set.rules()).extracting(Rule::id).containsExactly("a.one", "b.two");
        assertThatThrownBy(() -> new RuleSet(REF, true, List.of(rule("a.one"), rule("a.one"))))
                .hasMessage("Two rules with the id a.one");
    }

    @Test
    void askingForARuleThePackDoesNotHaveIsABug() {
        assertThatThrownBy(() -> new RuleSet(REF, true, List.of()).rule("policy.switch.height"))
                .hasMessage("Rule pack test-pack 1 has no rule policy.switch.height for VIC");
    }

    @Test
    void parametersAreReadByType() {
        Rule rule = Rule.builder().id("a.one").tier(Tier.MANDATORY).kind(RuleKind.EXCLUSION).cite("x")
                .parameter("zone", "bath-zone-2").parameter("value_mm", 600).source("test").build();
        assertThat(rule.text("zone")).isEqualTo("bath-zone-2");
        assertThat(rule.number("value_mm")).isEqualTo(600);
        assertThat(rule.parameter("missing")).isEmpty();
        assertThatThrownBy(() -> rule.number("zone")).hasMessage("Rule a.one (test) has no number parameter 'zone'");
        assertThatThrownBy(() -> rule.text("value_mm")).hasMessage("Rule a.one (test) has no text parameter 'value_mm'");
    }

    @Test
    void parametersAreDeeplyImmutableAndSorted() {
        List<Object> list = new ArrayList<>(List.of("gpo"));
        Map<String, Object> nested = new LinkedHashMap<>(Map.of("b", 1));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("z_list", list);
        parameters.put("a_map", nested);
        Rule rule = Rule.builder().id("a.one").tier(Tier.POLICY).kind(RuleKind.TABLE).cite("x").parameters(parameters).source("t").build();

        list.add("changed");
        nested.put("c", 2);
        assertThat(rule.parameters().keySet()).containsExactly("a_map", "z_list");
        assertThat(rule.parameters().get("z_list")).isEqualTo(List.of("gpo"));
        assertThat(rule.parameters().get("a_map")).isEqualTo(Map.of("b", 1));
        assertThatThrownBy(() -> ((List<Object>) rule.parameters().get("z_list")).add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> rule.parameters().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aFixedFieldIsNotAParameter() {
        assertThatThrownBy(() -> Rule.builder().id("a.one").tier(Tier.POLICY).kind(RuleKind.VALUE).cite("x")
                .parameter("tier", "mandatory").source("t").build())
                .hasMessage("rule a.one: 'tier' is a fixed field, not a parameter");
    }
}
