/**
 * Rule packs: the YAML packs under {@code src/main/resources/rulepacks/}, their
 * schemas, the three tiers and their precedence, company policy values, and
 * which states the engine designs. Rules are data; this module reads and
 * checks them, the modules that design and validate apply them
 * (documents/electrical-engine-plan.md §2.3–2.4, §6.9, E0-S3).
 *
 * <p>For other modules: {@link com.hypex.electriplan.rules.RuleBook} (the
 * Spring bean: designable states, rules for a state),
 * {@link com.hypex.electriplan.rules.RuleSet} and
 * {@link com.hypex.electriplan.rules.Rule}. Loading
 * ({@link com.hypex.electriplan.rules.RulePackLoader}) and precedence are plain
 * Java with no Spring.
 *
 * <p>Precedence: a state file adds rules and replaces national ones it marks
 * {@code override: true}, never making a mandatory rule optional; company
 * policy changes values of policy rules only. The states designed are the
 * states whose file is signed off: no state is named in code.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Rule packs", allowedDependencies = "model")
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.rules;
