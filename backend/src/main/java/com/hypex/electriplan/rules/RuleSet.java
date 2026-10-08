package com.hypex.electriplan.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.design.RulePackRef;

/**
 * The rules a design is made with, for one state: national rules, the state's
 * own and its overrides, and (optionally) a company's policy values. Sorted by
 * id, so the same pack always gives the same rule set.
 *
 * @param ref what a design records about these rules (pack, version, state, standards)
 * @param signedOff whether the state's rules are signed off; false only when
 *        unsigned states are allowed (development)
 */
public record RuleSet(RulePackRef ref, boolean signedOff, List<Rule> rules) {

    public RuleSet {
        Checks.required(ref, "ref");
        List<Rule> sorted = new ArrayList<>(Checks.list(rules, "rules"));
        sorted.sort(Comparator.comparing(Rule::id));
        Set<String> seen = new HashSet<>();
        for (Rule rule : sorted) {
            if (!seen.add(rule.id())) {
                throw new IllegalArgumentException("Two rules with the id " + rule.id());
            }
        }
        rules = List.copyOf(sorted);
    }

    public Optional<Rule> find(String id) {
        return rules.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /** The rule with this id; a stage asking for one the pack does not have is a bug. */
    public Rule rule(String id) {
        return find(id).orElseThrow(() -> new NoSuchElementException("Rule pack " + ref.id() + " " + ref.version()
                + " has no rule " + id + " for " + ref.state()));
    }

    public List<Rule> tier(Tier tier) {
        return rules.stream().filter(r -> r.tier() == tier).toList();
    }

    /**
     * These rules with a company's policy values on top. A company may change
     * the values of policy rules only: never a mandatory or regulatory rule,
     * never a rule's tier, kind or citation, and only values the rule has, to
     * values of the same type.
     *
     * @throws InvalidCompanyPolicyException listing every problem
     */
    public RuleSet withCompanyPolicy(CompanyPolicy policy) {
        Map<String, Rule> byId = new LinkedHashMap<>();
        rules.forEach(r -> byId.put(r.id(), r));
        List<String> problems = new ArrayList<>();
        Set<String> changed = new HashSet<>();

        for (CompanyPolicy.Change change : policy.changes()) {
            String id = change.id();
            Rule rule = byId.get(id);
            if (!changed.add(id)) {
                problems.add(id + ": changed twice");
                continue;
            }
            if (rule == null) {
                problems.add(id + ": no such rule in " + ref.id() + " " + ref.version() + " for " + ref.state());
                continue;
            }
            if (rule.tier() != Tier.POLICY) {
                problems.add(id + ": a " + rule.tier().code() + " rule; company policy can change policy rules only, never "
                        + rule.tier().code() + " ones");
                continue;
            }
            Map<String, Object> values = new TreeMap<>(rule.parameters());
            int before = problems.size();
            change.values().forEach((name, value) -> {
                Object current = rule.parameters().get(name);
                if (Rule.FIXED_FIELDS.contains(name)) {
                    problems.add(id + ": '" + name + "' cannot be changed, only the rule's values");
                } else if (current == null) {
                    problems.add(id + ": has no value '" + name + "' (it has " + String.join(", ", rule.parameters().keySet()) + ")");
                } else if (!Parameters.sameType(current, value)) {
                    problems.add(id + ": '" + name + "' must be " + Parameters.typeName(current) + ", not " + Parameters.typeName(value));
                } else {
                    values.put(name, value);
                }
            });
            if (problems.size() == before) {
                byId.put(id, rule.toBuilder().clearParameters().parameters(values).source(policy.source()).build());
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidCompanyPolicyException(policy.source(), problems);
        }
        return new RuleSet(ref, signedOff, List.copyOf(byId.values()));
    }
}
