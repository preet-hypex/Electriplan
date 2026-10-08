package com.hypex.electriplan.apidocs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The converter on its own, over the cases the API's records do not all cover yet. */
class RecordNullabilityTest {

    record Plain(String name, @Nullable String note, int count) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LeavesNullsOut(String name, @Nullable String note) {
    }

    record OneFieldLeavesNullsOut(String name, @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String note) {
    }

    record Renamed(@JsonProperty("full_name") String name) {
    }

    record Wrapped(@JsonValue String value) {
    }

    record Holder(Plain plain, @Nullable Plain maybe, Map<String, Object> extra, Wrapped code) {
    }

    ModelConverters converters;

    @BeforeEach
    void converters() {
        converters = new ModelConverters();
        converters.addConverter(new RecordNullability());
    }

    private Schema<?> schema(Class<?> type) {
        return converters.readAll(type).get(type.getSimpleName());
    }

    @Test
    void everyComponentIsRequiredAndOnlyNullableOnesAreNullable() {
        Schema<?> plain = schema(Plain.class);
        assertThat(plain.getRequired()).containsExactlyInAnyOrder("name", "note", "count");
        assertThat(plain.getProperties().get("note").getNullable()).isTrue();
        assertThat(plain.getProperties().get("name").getNullable()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void aNullableComponentLeftOutWhenNullIsOptional() {
        assertThat(schema(LeavesNullsOut.class).getRequired()).containsExactly("name");
        assertThat(schema(LeavesNullsOut.class).getProperties().get("note").getNullable()).isNotEqualTo(Boolean.TRUE);
        assertThat(schema(OneFieldLeavesNullsOut.class).getRequired()).containsExactly("name");
    }

    @Test
    void usesTheJsonName() {
        assertThat(schema(Renamed.class).getRequired()).containsExactly("full_name");
    }

    @Test
    void aNullableReferenceIsWrappedSoTheNullableFlagCounts() {
        Schema<?> holder = schema(Holder.class);
        assertThat(holder.getRequired()).containsExactlyInAnyOrder("plain", "maybe", "extra", "code");
        Schema<?> maybe = holder.getProperties().get("maybe");
        assertThat(maybe.getNullable()).isTrue();
        assertThat(maybe.getAllOf()).singleElement().extracting(Schema::get$ref).isEqualTo("#/components/schemas/Plain");
        assertThat(holder.getProperties().get("plain").get$ref()).isEqualTo("#/components/schemas/Plain");
    }

    @Test
    void aMapOfObjectsHoldsAnyJson() {
        assertThat(schema(Holder.class).getProperties().get("extra").getAdditionalProperties()).isEqualTo(true);
    }

    @Test
    void aValueRecordIsLeftAlone() {
        Schema<?> code = schema(Holder.class).getProperties().get("code");
        Schema<?> resolved = code.get$ref() == null ? code : schema(Wrapped.class);
        assertThat(resolved.getType()).isEqualTo("string");
    }
}
