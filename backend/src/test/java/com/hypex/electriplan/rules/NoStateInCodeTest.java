package com.hypex.electriplan.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.hypex.electriplan.model.common.AustralianState;
import org.junit.jupiter.api.Test;

/**
 * "Never hard-code VIC only" (engine plan, Expanding beyond Victoria): no
 * production code names a state, outside the enum that lists them all. Which
 * states the engine designs comes from the rule pack.
 */
class NoStateInCodeTest {

    static final Path MAIN = Path.of("src/main/java");
    static final Path ENUM = MAIN.resolve("com/hypex/electriplan/model/common/AustralianState.java");

    @Test
    void noProductionCodeNamesAState() throws IOException {
        String states = String.join("|", Stream.of(AustralianState.values()).map(Enum::name).toList());
        Pattern named = Pattern.compile("AustralianState\\.(" + states + ")\\b|\"(" + states + ")\"");

        List<String> offenders;
        try (Stream<Path> files = Files.walk(MAIN)) {
            offenders = files.filter(f -> f.toString().endsWith(".java") && !f.equals(ENUM))
                    .flatMap(f -> lines(f).filter(line -> named.matcher(line).find() && !line.trim().startsWith("*")
                            && !line.trim().startsWith("//")).map(line -> MAIN.relativize(f) + ": " + line.trim()))
                    .toList();
        }
        assertThat(offenders).as("Production code naming a state; take it from the rule pack instead").isEmpty();
    }

    private static Stream<String> lines(Path file) {
        try {
            return Files.readAllLines(file).stream();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
