package com.hypex.electriplan.model.design;

import java.util.List;
import java.util.regex.Pattern;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.AustralianState;

import lombok.Builder;
import lombok.Singular;
import lombok.With;

/**
 * The rules a design was made and checked with: the rule pack release, the
 * state whose rules were applied on top of the national ones, and the
 * standards editions they encode. Together they say exactly which rules made
 * the design, so a design is reproducible and its sign-off traceable after
 * more states are added.
 */
@Builder(toBuilder = true)
@With
public record RulePackRef(String id, String version, AustralianState state, @Singular List<String> standards) {

    public static final Pattern ID = Pattern.compile("^[a-z0-9-]+$");

    public RulePackRef {
        Checks.matches(id, ID, "rulePack.id");
        Checks.text(version, "rulePack.version");
        Checks.required(state, "rulePack.state");
        standards = Checks.list(standards, "standards");
        standards.forEach(s -> Checks.text(s, "standards[]"));
    }
}
