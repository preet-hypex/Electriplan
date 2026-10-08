package com.hypex.electriplan.rules;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.hypex.electriplan.model.Checks;

import lombok.Builder;
import lombok.Singular;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * One rule of a pack, as it applies after precedence: national, replaced by
 * the state's version where the state overrides it, with company policy
 * values on top.
 *
 * @param parameters everything on the rule besides its fixed fields: the
 *        values its kind needs (value_mm, applies_to, zone...). Immutable,
 *        sorted by name, nested lists and maps included.
 * @param verified checked against the licensed standard; false is a working
 *        assumption (the plan's ⚠)
 * @param source where this version of the rule came from: a pack file
 *        ({@code au-residential/state/VIC.yaml}) or a company policy
 */
@Builder(toBuilder = true)
@With
public record Rule(
        String id,
        Tier tier,
        RuleKind kind,
        String cite,
        @Nullable String message,
        boolean verified,
        @Singular Map<String, Object> parameters,
        String source) {

    /** The fields every rule has; anything else on a rule is a parameter. */
    public static final Set<String> FIXED_FIELDS = Set.of("id", "tier", "kind", "cite", "message", "verified", "override");

    public Rule {
        Checks.text(id, "rule.id");
        Checks.required(tier, "rule.tier");
        Checks.required(kind, "rule.kind");
        Checks.text(cite, "rule.cite");
        Checks.text(source, "rule.source");
        parameters = Parameters.copy(parameters, "rule " + id);
        for (String name : parameters.keySet()) {
            if (FIXED_FIELDS.contains(name)) {
                throw new IllegalArgumentException("rule " + id + ": '" + name + "' is a fixed field, not a parameter");
            }
        }
    }

    public Optional<Object> parameter(String name) {
        return Optional.ofNullable(parameters.get(name));
    }

    /** A number parameter, e.g. {@code number("value_mm")}. */
    public double number(String name) {
        if (parameters.get(name) instanceof Number n) {
            return n.doubleValue();
        }
        throw missing(name, "number");
    }

    /** A text parameter, e.g. {@code text("zone")}. */
    public String text(String name) {
        if (parameters.get(name) instanceof String s) {
            return s;
        }
        throw missing(name, "text");
    }

    private IllegalStateException missing(String name, String type) {
        return new IllegalStateException("Rule " + id + " (" + source + ") has no " + type + " parameter '" + name + "'");
    }
}
