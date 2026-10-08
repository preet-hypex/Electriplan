/**
 * Rule packs: loading the YAML packs, the three tiers and their precedence
 * (national, state, company policy), company overrides and the standards
 * register. Rules are data; this module reads and checks them, the modules
 * that design and validate apply them. Empty until E0-S3, E0-S6 and E0-S7.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Rule packs", allowedDependencies = "model")
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.rules;
