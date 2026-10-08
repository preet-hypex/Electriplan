package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypex.electriplan.model.Contracts.Document;
import com.hypex.electriplan.model.Contracts.Example;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The JSON Schemas in contracts/ and the Java model agree: on every valid
 * example, on every invalid one, and on whatever Java builds. If the schema
 * and the records drift apart, one of these fails.
 */
class ContractTest {

    static Stream<Example> validExamples() {
        return Contracts.examples(true).stream();
    }

    static Stream<Example> invalidExamples() {
        return Contracts.examples(false).stream();
    }

    @ParameterizedTest(name = "{0} is itself a valid 2020-12 JSON Schema")
    @EnumSource(Document.class)
    void schemasAreValidSchemas(Document document) {
        JsonNode schema = Contracts.tree(new String(readClasspath("contracts/" + document.name + ".schema.json")));
        Set<ValidationMessage> errors = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"))
                .validate(schema);
        assertThat(errors).isEmpty();
    }

    @ParameterizedTest(name = "every document has valid and invalid examples: {0}")
    @EnumSource(Document.class)
    void everyDocumentHasExamplesBothWays(Document document) {
        assertThat(Contracts.examples(document, true)).isNotEmpty();
        assertThat(Contracts.examples(document, false)).isNotEmpty();
    }

    @ParameterizedTest(name = "schema accepts {0}")
    @MethodSource("validExamples")
    void theSchemaAcceptsEveryValidExample(Example example) {
        assertThat(Contracts.validate(example.document(), example.json())).isEmpty();
    }

    @ParameterizedTest(name = "schema refuses {0}")
    @MethodSource("invalidExamples")
    void theSchemaRefusesEveryInvalidExample(Example example) {
        assertThat(Contracts.validate(example.document(), example.json())).isNotEmpty();
    }

    @ParameterizedTest(name = "Java reads and writes back {0} unchanged")
    @MethodSource("validExamples")
    void javaRoundTripsEveryValidExampleUnchanged(Example example) {
        Object model = ModelJson.read(example.json(), example.document().type);
        String written = ModelJson.write(model);

        assertThat(Contracts.tree(written).equals(Contracts.SAME_VALUE, Contracts.tree(example.json())))
                .as("written back:%n%s%nexpected:%n%s", written, example.json())
                .isTrue();
        assertThat(Contracts.validate(example.document(), written)).isEmpty();
    }

    @ParameterizedTest(name = "Java refuses {0}")
    @MethodSource("invalidExamples")
    void javaRefusesEveryInvalidExample(Example example) {
        assertThatThrownBy(() -> ModelJson.read(example.json(), example.document().type))
                .isInstanceOf(ModelJson.ModelJsonException.class);
    }

    @Test
    void whatJavaBuildsIsValid() {
        assertThat(Contracts.validate(Document.ELECTRICAL_DESIGN, ModelJson.write(Samples.fullDesign()))).isEmpty();
        assertThat(Contracts.validate(Document.PROJECT_BRIEF, ModelJson.write(Samples.brief()))).isEmpty();
        assertThat(Contracts.validate(Document.FIXTURE, ModelJson.write(Samples.shower()))).isEmpty();
        assertThat(Contracts.validate(Document.FIXTURE, ModelJson.write(Samples.cooktop()))).isEmpty();
    }

    @Test
    void whatJavaBuildsReadsBackEqual() {
        assertThat(ModelJson.read(ModelJson.write(Samples.fullDesign()), com.hypex.electriplan.model.design.ElectricalDesign.class))
                .isEqualTo(Samples.fullDesign());
        assertThat(ModelJson.read(ModelJson.write(Samples.brief()), com.hypex.electriplan.model.brief.ProjectBrief.class))
                .isEqualTo(Samples.brief());
    }

    @Test
    void bothWallAndCeilingPlacementsAreCovered() {
        List<String> types = Contracts.examples(Document.ELECTRICAL_DESIGN, true).stream()
                .flatMap(e -> Contracts.tree(e.json()).path("points").findValuesAsText("type").stream())
                .distinct().toList();
        assertThat(types).contains("wall", "ceiling");
    }

    private static byte[] readClasspath(String path) {
        try (var in = ContractTest.class.getClassLoader().getResourceAsStream(path)) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException(path, e);
        }
    }
}
