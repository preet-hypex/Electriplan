package com.hypex.electriplan.design;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hypex.electriplan.model.ModelJson;
import com.hypex.electriplan.model.design.PlanRef;
import com.hypex.electriplan.model.plan.FloorPlan;

/**
 * Identifies the exact floor plan a design was made from, so a design made
 * from an older plan can be detected (§2 ground rule 8: the design references
 * the plan, never copies or edits it).
 *
 * <p>The hash is {@code "sha256:"} and the SHA-256 of the plan's canonical
 * JSON: the plan as {@link ModelJson} writes it, with the keys of every object
 * sorted, no whitespace, UTF-8. It depends only on the plan's content, so two
 * equal plans have the same hash however they were built or read.
 */
public final class PlanFingerprint {

    private static final JsonMapper MAPPER = ModelJson.mapper();

    private PlanFingerprint() {
    }

    /** The reference a design made from this plan revision records. */
    public static PlanRef planRef(FloorPlan plan, int planRevision) {
        return new PlanRef(hash(plan), planRevision);
    }

    /** {@code "sha256:"} and 64 lowercase hex digits. */
    public static String hash(FloorPlan plan) {
        return "sha256:" + HexFormat.of().formatHex(sha256(canonicalJson(plan)));
    }

    /** The plan as the bytes that are hashed. */
    static byte[] canonicalJson(FloorPlan plan) {
        try {
            return MAPPER.writeValueAsString(sorted(MAPPER.valueToTree(plan))).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write the floor plan", e);
        }
    }

    private static JsonNode sorted(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Map<String, JsonNode> fields = new TreeMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = object.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                fields.put(field.getKey(), sorted(field.getValue()));
            }
            ObjectNode copy = MAPPER.createObjectNode();
            fields.forEach(copy::set);
            return copy;
        }
        if (node instanceof ArrayNode array) {
            ArrayNode copy = MAPPER.createArrayNode();
            array.forEach(element -> copy.add(sorted(element)));
            return copy;
        }
        return node;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }
}
