package com.hypex.electriplan.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypex.electriplan.model.brief.ProjectBrief;
import com.hypex.electriplan.model.design.ElectricalDesign;
import com.hypex.electriplan.model.fixture.Fixture;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Test access to the contracts in {@code ../contracts}, on the test classpath
 * as {@code classpath:contracts/}: the schemas, their example documents, and
 * the Java type each schema describes.
 */
public final class Contracts {

    /** A schema and the record that follows it. */
    public enum Document {
        PROJECT_BRIEF("project-brief", ProjectBrief.class),
        FIXTURE("fixture", Fixture.class),
        ELECTRICAL_DESIGN("electrical-design", ElectricalDesign.class);

        public final String name;
        public final Class<?> type;

        Document(String name, Class<?> type) {
            this.name = name;
            this.type = type;
        }

        public String schemaLocation() {
            return "classpath:contracts/" + name + ".schema.json";
        }
    }

    /** One example file: which document it is, and whether it is meant to be valid. */
    public record Example(Document document, boolean valid, String file, String json) {
        @Override
        public String toString() {
            return document.name + "/" + (valid ? "valid/" : "invalid/") + file;
        }
    }

    public static final ObjectMapper PLAIN = new ObjectMapper();

    /** Numbers compare by value, so 10 and 10.0 are the same; everything else by equality. */
    public static final Comparator<JsonNode> SAME_VALUE = (a, b) -> {
        if (a.isNumber() && b.isNumber()) {
            return Double.compare(a.doubleValue(), b.doubleValue());
        }
        return a.equals(b) ? 0 : 1;
    };

    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final PathMatchingResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver();

    private Contracts() {
    }

    public static JsonSchema schema(Document document) {
        return FACTORY.getSchema(SchemaLocation.of(document.schemaLocation()));
    }

    public static Set<ValidationMessage> validate(Document document, String json) {
        return schema(document).validate(tree(json));
    }

    public static List<Example> examples(boolean valid) {
        return Arrays.stream(Document.values())
                .flatMap(d -> examples(d, valid).stream())
                .toList();
    }

    public static List<Example> examples(Document document, boolean valid) {
        String pattern = "classpath:contracts/examples/" + document.name + "/" + (valid ? "valid" : "invalid") + "/*.json";
        try {
            return Arrays.stream(RESOLVER.getResources(pattern))
                    .map(r -> new Example(document, valid, r.getFilename(), read(r)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JsonNode tree(String json) {
        try {
            return PLAIN.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The JSON at a pointer inside a schema file, e.g. the enum of a property. */
    public static JsonNode schemaNode(String file, String pointer) {
        try (InputStream in = RESOLVER.getResource("classpath:contracts/" + file).getInputStream()) {
            JsonNode node = PLAIN.readTree(in).at(pointer);
            if (node.isMissingNode()) {
                throw new IllegalArgumentException("No " + pointer + " in " + file);
            }
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Resource r) {
        try (InputStream in = r.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
