package com.hypex.electriplan.apidocs;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Required and nullable fields of records, from JSpecify: springdoc cannot see
 * {@code @Nullable} (a type-use annotation), so on its own it would mark every
 * field optional. Here every record component is required (Jackson always
 * writes it), and a {@code @Nullable} one is also nullable; unless the record
 * leaves nulls out of its JSON ({@code @JsonInclude(NON_NULL)}), when a
 * {@code @Nullable} component is optional instead.
 * A {@code Map<String, Object>} field may hold any JSON values.
 */
@Component
class RecordNullability implements ModelConverter {

    @Override
    public @Nullable Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema<?> schema = chain.next().resolve(type, context, chain);
        Class<?> raw = TypeFactory.defaultInstance().constructType(type.getType()).getRawClass();
        if (schema == null || raw == null || !raw.isRecord() || hasJsonValue(raw)) {
            return schema;
        }
        Schema<?> model = schema;
        if (schema.get$ref() != null) {
            model = context.getDefinedModels().get(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
        }
        if (model == null || model.getProperties() == null) {
            return schema;
        }
        boolean omitsNulls = omitsNulls(raw.getAnnotation(JsonInclude.class));
        for (RecordComponent component : raw.getRecordComponents()) {
            String name = jsonName(component);
            Schema<?> property = model.getProperties().get(name);
            if (property == null) {
                continue;
            }
            if (Map.class.isAssignableFrom(component.getType()) && isEmptyObject(property.getAdditionalProperties())) {
                property.setAdditionalProperties(true); // Map<String, Object>: any JSON value
            }
            boolean nullable = component.getAnnotatedType().isAnnotationPresent(Nullable.class);
            if (nullable && (omitsNulls || omitsNulls(annotation(component, JsonInclude.class)))) {
                continue; // left out when null: optional
            }
            if (nullable) {
                model.getProperties().put(name, nullableVersionOf(property));
            }
            if (model.getRequired() == null || !model.getRequired().contains(name)) {
                model.addRequiredItem(name);
            }
        }
        return schema;
    }

    private static Schema<?> nullableVersionOf(Schema<?> property) {
        if (property.get$ref() == null) {
            return property.nullable(true);
        }
        // OpenAPI 3.0 ignores siblings of $ref, so wrap it.
        return new Schema<>().allOf(List.of(new Schema<>().$ref(property.get$ref()))).nullable(true);
    }

    private static boolean isEmptyObject(@Nullable Object schema) {
        return schema instanceof Schema<?> s && "object".equals(s.getType()) && s.getProperties() == null && s.get$ref() == null;
    }

    private static boolean omitsNulls(@Nullable JsonInclude include) {
        return include != null && include.value() != JsonInclude.Include.ALWAYS
                && include.value() != JsonInclude.Include.USE_DEFAULTS;
    }

    private static boolean hasJsonValue(Class<?> record) {
        for (RecordComponent component : record.getRecordComponents()) {
            if (annotation(component, JsonValue.class) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * A Jackson annotation on a record component: Java puts it on the accessor
     * or the field (Jackson's annotations do not target record components).
     */
    private static <A extends Annotation> @Nullable A annotation(RecordComponent component, Class<A> type) {
        A onAccessor = component.getAccessor().getAnnotation(type);
        if (onAccessor != null) {
            return onAccessor;
        }
        try {
            return component.getDeclaringRecord().getDeclaredField(component.getName()).getAnnotation(type);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static String jsonName(RecordComponent component) {
        JsonProperty renamed = annotation(component, JsonProperty.class);
        return renamed != null && !renamed.value().isEmpty() ? renamed.value() : component.getName();
    }
}
