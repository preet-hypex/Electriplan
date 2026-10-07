package com.hypex.electriplan.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Reads and writes the model as JSON, strictly: anything the schemas would
 * reject is refused here too. Unknown properties, a number where a string
 * belongs ("12" for 12, 1.5 for an integer), an unknown enum value, a missing
 * required field, a key given twice: each is an error, never silently coerced. Absent optional
 * values are left out of the output rather than written as null.
 *
 * <p>Deliberately not the application's Spring ObjectMapper, which is lenient
 * for HTTP; use this one for model documents.
 */
public final class ModelJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private ModelJson() {
    }

    /** The shared, thread-safe mapper. Do not reconfigure it. */
    public static JsonMapper mapper() {
        return MAPPER;
    }

    /** Parses a document; any problem, including a broken invariant, is a {@link ModelJsonException}. */
    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ModelJsonException("Not a valid " + type.getSimpleName() + ": " + rootMessage(e), e);
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ModelJsonException("Could not write " + value.getClass().getSimpleName(), e);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }

    /** A model document that could not be read or written. */
    public static final class ModelJsonException extends RuntimeException {
        public ModelJsonException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
