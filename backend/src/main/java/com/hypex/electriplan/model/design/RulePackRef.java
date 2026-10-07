package com.hypex.electriplan.model.design;

import java.util.List;
import java.util.regex.Pattern;

import com.hypex.electriplan.model.Checks;

import lombok.Builder;
import lombok.Singular;
import lombok.With;

/** The rule pack a design was made and checked with, and the standards editions it encodes. */
@Builder(toBuilder = true)
@With
public record RulePackRef(String id, String version, @Singular List<String> standards) {

    public static final Pattern ID = Pattern.compile("^[a-z0-9-]+$");

    public RulePackRef {
        Checks.matches(id, ID, "rulePack.id");
        Checks.text(version, "rulePack.version");
        standards = Checks.list(standards, "standards");
        standards.forEach(s -> Checks.text(s, "standards[]"));
    }
}
