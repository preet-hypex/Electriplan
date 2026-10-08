package com.hypex.electriplan.rules;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small rule packs built in memory for tests: a meta.yaml and rule files. */
final class Packs {

    static final String PACK = "test-pack";

    private final Map<String, String> files = new LinkedHashMap<>();
    private final Map<String, String> states = new LinkedHashMap<>();
    private String national = "[base.yaml]";
    private String status = "draft";

    static Packs pack() {
        return new Packs().file("base.yaml", """
                rules:
                  - id: wet.zone.height
                    tier: mandatory
                    kind: value
                    value_mm: 2250
                    cite: "AS/NZS 3000:2018 cl. 6.2.2"
                  - id: policy.switch.height
                    tier: policy
                    kind: value
                    value_mm: 1100
                    cite: "Company standard"
                  - id: policy.gpo.corner-clearance
                    tier: policy
                    kind: clearance
                    applies_to: [gpo-double]
                    distance_mm: 300
                    cite: "Company standard"
                """);
    }

    Packs file(String path, String yaml) {
        files.put(path, yaml);
        return this;
    }

    Packs without(String path) {
        files.remove(path);
        return this;
    }

    Packs national(String yamlList) {
        this.national = yamlList;
        return this;
    }

    Packs status(String status) {
        this.status = status;
        return this;
    }

    /** A state file, unsigned. */
    Packs state(String state, String rulesYaml) {
        files.put("state/" + state + ".yaml", rulesYaml);
        states.put(state, "    file: state/" + state + ".yaml\n");
        return this;
    }

    /** A state file signed off as it is now. */
    Packs signedState(String state, String rulesYaml) {
        state(state, rulesYaml);
        return signOff(state, RulePackLoader.sha256(rulesYaml.getBytes(StandardCharsets.UTF_8)));
    }

    Packs signOff(String state, String sha256) {
        states.put(state, "    file: state/" + state + ".yaml\n"
                + "    signOff:\n"
                + "      by: Pat Sparks\n"
                + "      licence: REC-12345\n"
                + "      date: 2026-11-02\n"
                + "      sha256: " + sha256 + "\n");
        return this;
    }

    Packs meta(String yaml) {
        files.put("meta.yaml", yaml);
        return this;
    }

    RulePackSource source() {
        Map<String, String> all = new LinkedHashMap<>(files);
        all.putIfAbsent("meta.yaml", metaYaml());
        return RulePackSource.of(PACK, all);
    }

    String metaYaml() {
        StringBuilder meta = new StringBuilder("""
                id: test-pack
                version: 2026.1-test
                status: %s
                standards:
                  - code: AS/NZS 3000
                    edition: 2018+A3
                national: %s
                """.formatted(status, national));
        meta.append(states.isEmpty() ? "states: {}\n" : "states:\n");
        states.forEach((state, entry) -> meta.append("  ").append(state).append(":\n").append(entry));
        return meta.toString();
    }

    RulePack load() {
        return RulePackLoader.load(source());
    }

    static final String NO_RULES = "rules: []\n";
}
