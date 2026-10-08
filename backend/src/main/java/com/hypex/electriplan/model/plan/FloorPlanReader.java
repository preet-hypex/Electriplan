package com.hypex.electriplan.model.plan;

import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.hypex.electriplan.model.ModelJson;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * How a floor plan comes into the engine. In order: the JSON must parse; its
 * {@code version} must be one this build reads ({@link FloorPlan#SUPPORTED_VERSIONS}),
 * else {@link UnsupportedFloorPlanVersionException}; it must be valid against
 * {@code contracts/floor-plan.schema.json}; and it must make valid
 * {@link FloorPlan} records. Any other failure is an {@link InvalidFloorPlanException}
 * listing every problem the schema found, so a caller can show them all at once.
 *
 * <p>The schema is the one in {@code contracts/}, packaged into the application
 * at build time, so the engine checks plans against exactly what the analyser
 * and the editor are tested against. Thread-safe.
 */
public final class FloorPlanReader {

    /** Where the packaged schema is; {@code common.schema.json} sits beside it. */
    public static final String SCHEMA = "classpath:contracts/floor-plan.schema.json";

    private static final JsonSchema VALIDATOR = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(SchemaLocation.of(SCHEMA));

    private FloorPlanReader() {
    }

    /** Reads a floor plan from JSON, or says exactly why it cannot. */
    public static FloorPlan read(String json) {
        JsonNode tree;
        try {
            tree = ModelJson.mapper().readTree(json);
        } catch (JsonProcessingException e) {
            throw new InvalidFloorPlanException(List.of("not JSON: " + e.getOriginalMessage()), e);
        }
        return read(tree);
    }

    /** Reads a floor plan from parsed JSON, or says exactly why it cannot. */
    public static FloorPlan read(JsonNode tree) {
        if (tree == null || !tree.isObject()) {
            throw new InvalidFloorPlanException(List.of("expected a JSON object"));
        }
        checkVersion(tree.get("version"));

        List<String> problems = VALIDATOR.validate(tree).stream()
                .sorted(Comparator.comparing((ValidationMessage m) -> m.getInstanceLocation().toString())
                        .thenComparing(ValidationMessage::getMessage))
                .map(ValidationMessage::getMessage)
                .toList();
        if (!problems.isEmpty()) {
            throw new InvalidFloorPlanException(problems);
        }

        try {
            return ModelJson.mapper().treeToValue(tree, FloorPlan.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            // The schema and the records agree (ContractTest), so this is a rule
            // the schema cannot state.
            throw new InvalidFloorPlanException(List.of(rootMessage(e)), e);
        }
    }

    /** A version that is a whole number but not one we read is its own error; anything else the schema reports. */
    private static void checkVersion(JsonNode version) {
        if (version != null && version.isIntegralNumber() && version.canConvertToLong()) {
            long v = version.longValue();
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE || !FloorPlan.SUPPORTED_VERSIONS.contains((int) v)) {
                throw new UnsupportedFloorPlanVersionException(v, FloorPlan.SUPPORTED_VERSIONS);
            }
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }
}
