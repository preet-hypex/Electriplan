package com.hypex.electriplan.rules;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.hypex.electriplan.model.common.AustralianState;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.jspecify.annotations.Nullable;

/**
 * Reads a rule pack and checks all of it before anything uses it: meta.yaml
 * and every rule file against their schemas (rulepacks/schema/), then the
 * rules between files. Every problem in every file is collected and reported
 * together, each naming its file and rule.
 *
 * <ul>
 *   <li>Every rule has an {@code id}, {@code tier}, {@code kind} and {@code cite}.</li>
 *   <li>Ids are unique: in the national files together, and in each state file.</li>
 *   <li>National files hold mandatory and policy rules; regulatory rules are
 *       state variations and belong in state files.</li>
 *   <li>A state rule with a national rule's id must say {@code override: true},
 *       and an override must replace a national rule of the same kind. It may
 *       not make a mandatory rule a policy rule.</li>
 *   <li>A state is signed off only when meta.yaml records a sign-off whose
 *       SHA-256 is that of the file now. A changed file loses its sign-off
 *       (reported in {@link RulePack#notices()}), and the state its support.</li>
 * </ul>
 *
 * Plain Java: no Spring, and no I/O beyond the {@link RulePackSource}.
 */
public final class RulePackLoader {

    private static final YAMLMapper YAML = YAMLMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private static final JsonSchemaFactory SCHEMAS = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final JsonSchema META = SCHEMAS.getSchema(SchemaLocation.of("classpath:rulepacks/schema/meta.schema.json"));
    private static final JsonSchema RULE_FILE = SCHEMAS.getSchema(SchemaLocation.of("classpath:rulepacks/schema/rule-file.schema.json"));

    private static final Pattern RULE_LOCATION = Pattern.compile("^\\$\\.rules\\[(\\d+)]");

    private RulePackLoader() {
    }

    /** The checked pack, or an {@link InvalidRulePackException} listing every problem. */
    public static RulePack load(RulePackSource source) {
        return new Run(source).load();
    }

    /** SHA-256 of a rule file's bytes, as a sign-off records it. */
    public static String sha256(byte[] file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /** One load: the problems found so far. */
    private static final class Run {

        private final RulePackSource source;
        private final List<String> problems = new ArrayList<>();
        private final List<String> notices = new ArrayList<>();

        Run(RulePackSource source) {
            this.source = source;
        }

        RulePack load() {
            JsonNode meta = document("meta.yaml", META);
            if (meta == null) {
                throw invalid();
            }
            String id = meta.path("id").asText();
            if (!id.equals(source.name())) {
                problems.add("meta.yaml: id is " + id + " but the pack is " + source.name());
            }
            String version = meta.path("version").asText();

            Map<String, Rule> national = new LinkedHashMap<>();
            for (JsonNode file : meta.path("national")) {
                for (Parsed parsed : rules(id, file.asText(), false)) {
                    Rule rule = parsed.rule();
                    Rule earlier = national.putIfAbsent(rule.id(), rule);
                    if (earlier != null) {
                        problems.add(file.asText() + ": rule " + rule.id() + " is also in " + earlier.source());
                    }
                }
            }

            Map<AustralianState, StateRules> states = new EnumMap<>(AustralianState.class);
            meta.path("states").properties().forEach(entry -> {
                AustralianState state = AustralianState.valueOf(entry.getKey());
                StateRules rules = state(id, state, entry.getValue(), national);
                if (rules != null) {
                    states.put(state, rules);
                }
            });

            if (!problems.isEmpty()) {
                throw invalid();
            }
            return new RulePack(id, version, RulePack.Status.valueOf(meta.path("status").asText().toUpperCase(java.util.Locale.ROOT)),
                    standards(meta), List.copyOf(national.values()), states, notices);
        }

        private @Nullable StateRules state(String pack, AustralianState state, JsonNode entry, Map<String, Rule> national) {
            String file = entry.path("file").asText();
            if (!file.equals("state/" + state + ".yaml")) {
                problems.add("meta.yaml: " + state + "'s rules must be in state/" + state + ".yaml, not " + file);
                return null;
            }
            Optional<byte[]> bytes = source.read(file);
            List<Parsed> rules = rules(pack, file, true);
            if (bytes.isEmpty()) {
                return null;
            }
            Map<String, Parsed> own = new LinkedHashMap<>();
            for (Parsed parsed : rules) {
                if (own.putIfAbsent(parsed.rule().id(), parsed) != null) {
                    problems.add(file + ": rule " + parsed.rule().id() + " is there twice");
                }
            }
            List<Rule> checked = new ArrayList<>();
            for (Parsed parsed : own.values()) {
                if (precedenceAllows(file, parsed.rule(), parsed.override(), national.get(parsed.rule().id()))) {
                    checked.add(parsed.rule());
                }
            }

            SignOff signOff = signOff(file, entry.get("signOff"));
            boolean signedOff = false;
            if (signOff != null) {
                signedOff = signOff.sha256().equals(sha256(bytes.get()));
                if (!signedOff) {
                    notices.add(file + " has changed since " + signOff.by() + " signed it off on " + signOff.date()
                            + ": " + state + " is not supported until it is signed off again");
                }
            }
            return new StateRules(state, file, checked, signOff, signedOff);
        }

        /** Whether the state file's rule may sit beside (or replace) the national rule with its id. */
        private boolean precedenceAllows(String file, Rule rule, boolean override, @Nullable Rule national) {
            String where = file + ": rule " + rule.id();
            if (national == null) {
                if (override) {
                    problems.add(where + " says override, but there is no national rule " + rule.id() + " to replace");
                    return false;
                }
                return true;
            }
            if (!override) {
                problems.add(where + " has the id of a national rule (" + national.source()
                        + "): add 'override: true' to replace it, or give it its own id");
                return false;
            }
            if (rule.kind() != national.kind()) {
                problems.add(where + " is a " + rule.kind().code() + " rule but replaces a " + national.kind().code() + " rule");
                return false;
            }
            if (national.tier() == Tier.MANDATORY && rule.tier() == Tier.POLICY) {
                problems.add(where + " would make a mandatory rule a policy rule: a state may change a mandatory rule's"
                        + " values, never make it optional");
                return false;
            }
            return true;
        }

        /** A rule as read, with whether it says it overrides a national rule. */
        private record Parsed(Rule rule, boolean override) {
        }

        /** The rules of one file; nothing if the file is missing or invalid (its problems recorded). */
        private List<Parsed> rules(String pack, String file, boolean stateFile) {
            JsonNode tree = document(file, RULE_FILE);
            if (tree == null) {
                return List.of();
            }
            List<Parsed> rules = new ArrayList<>();
            for (JsonNode node : tree.path("rules")) {
                Rule rule = rule(pack + "/" + file, node);
                boolean override = node.path("override").asBoolean(false);
                if (!stateFile && rule.tier() == Tier.REGULATORY) {
                    problems.add(file + ": rule " + rule.id() + " is regulatory: state variations belong in a state file");
                } else if (!stateFile && override) {
                    problems.add(file + ": rule " + rule.id() + " says override: only a state file can replace a national rule");
                } else {
                    rules.add(new Parsed(rule, override));
                }
            }
            return rules;
        }

        /** A file parsed and checked against its schema; null (with its problems recorded) if it is missing or invalid. */
        private @Nullable JsonNode document(String file, JsonSchema schema) {
            Optional<byte[]> bytes = source.read(file);
            if (bytes.isEmpty()) {
                problems.add(file + ": missing");
                return null;
            }
            JsonNode tree;
            try {
                tree = YAML.readTree(new String(bytes.get(), StandardCharsets.UTF_8));
            } catch (JacksonException e) {
                problems.add(file + ": not valid YAML: " + e.getOriginalMessage());
                return null;
            }
            if (tree == null || tree.isMissingNode() || tree.isNull()) {
                problems.add(file + ": empty");
                return null;
            }
            List<ValidationMessage> messages = schema.validate(tree).stream()
                    .sorted(Comparator.comparing((ValidationMessage m) -> m.getInstanceLocation().toString())
                            .thenComparing(ValidationMessage::getMessage))
                    .toList();
            for (ValidationMessage message : messages) {
                problems.add(file + ": " + message.getMessage() + ruleHint(tree, message));
            }
            return messages.isEmpty() ? tree : null;
        }

        /** " (rule wet.gpo.zone2)" when the problem is inside a rule that has an id. */
        private static String ruleHint(JsonNode tree, ValidationMessage message) {
            Matcher m = RULE_LOCATION.matcher(message.getInstanceLocation().toString());
            if (!m.find()) {
                return "";
            }
            JsonNode id = tree.path("rules").path(Integer.parseInt(m.group(1))).get("id");
            return id != null && id.isTextual() ? " (rule " + id.asText() + ")" : "";
        }

        private static Rule rule(String source, JsonNode node) {
            Map<String, Object> parameters = new LinkedHashMap<>();
            node.properties().forEach(field -> {
                if (!Rule.FIXED_FIELDS.contains(field.getKey())) {
                    parameters.put(field.getKey(), YAML.convertValue(field.getValue(), new TypeReference<Object>() { }));
                }
            });
            return Rule.builder()
                    .id(node.path("id").asText())
                    .tier(Tier.valueOf(node.path("tier").asText().toUpperCase(java.util.Locale.ROOT)))
                    .kind(RuleKind.valueOf(node.path("kind").asText().toUpperCase(java.util.Locale.ROOT)))
                    .cite(node.path("cite").asText())
                    .message(node.hasNonNull("message") ? node.get("message").asText() : null)
                    .verified(node.path("verified").asBoolean(false))
                    .parameters(parameters)
                    .source(source)
                    .build();
        }

        private @Nullable SignOff signOff(String file, @Nullable JsonNode node) {
            if (node == null) {
                return null;
            }
            try {
                return new SignOff(node.path("by").asText(), node.path("licence").asText(),
                        LocalDate.parse(node.path("date").asText()), node.path("sha256").asText());
            } catch (DateTimeParseException e) {
                problems.add("meta.yaml: the sign-off of " + file + " has no real date: " + node.path("date").asText());
                return null;
            }
        }

        private static List<RulePack.Standard> standards(JsonNode meta) {
            List<RulePack.Standard> standards = new ArrayList<>();
            meta.path("standards").forEach(s -> standards.add(new RulePack.Standard(
                    s.path("code").asText(), s.path("edition").asText(), s.path("verified").asBoolean(false))));
            return standards;
        }

        private InvalidRulePackException invalid() {
            return new InvalidRulePackException(source.name(), problems);
        }
    }
}
