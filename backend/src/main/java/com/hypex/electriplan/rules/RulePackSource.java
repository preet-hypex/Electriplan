package com.hypex.electriplan.rules;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Where a rule pack's files are read from: the classpath, or (for tests) memory. */
public interface RulePackSource {

    /** The pack's name, which its meta.yaml id must match (e.g. au-residential). */
    String name();

    /** A file of the pack by its path inside the pack (meta.yaml, state/VIC.yaml), if there is one. */
    Optional<byte[]> read(String path);

    /** The pack in {@code rulepacks/<name>/} on the classpath. */
    static RulePackSource classpath(String name) {
        ClassLoader loader = RulePackSource.class.getClassLoader();
        return new RulePackSource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Optional<byte[]> read(String path) {
                try (InputStream in = loader.getResourceAsStream("rulepacks/" + name + "/" + path)) {
                    return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot read rule pack file " + name + "/" + path, e);
                }
            }
        };
    }

    /** A pack from file contents by path. */
    static RulePackSource of(String name, Map<String, String> files) {
        Map<String, String> copy = new TreeMap<>(files);
        return new RulePackSource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Optional<byte[]> read(String path) {
                return Optional.ofNullable(copy.get(path)).map(s -> s.getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}
