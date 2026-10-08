package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChecksTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "wall_004", "lt_001", "c_L1", "Room-7", "x23456789012345678901234567890123456789012345678901234567890123"})
    void acceptsIdentifiers(String id) {
        assertThat(Checks.id(id, "id")).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1room", "_x", "-x", "has space", "dot.ted", "ü", "x2345678901234567890123456789012345678901234567890123456789012345"})
    void refusesWhatTheSchemaRefusesAsAnId(String id) {
        assertThatThrownBy(() -> Checks.id(id, "id")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id");
    }

    @Test
    void namesTheMissingField() {
        assertThatThrownBy(() -> Checks.required(null, "planRef")).hasMessage("planRef is required");
        assertThatThrownBy(() -> Checks.text("  ", "label")).hasMessage("label must not be blank");
    }

    @Test
    void listsAreImmutableCopies() {
        List<String> source = new ArrayList<>(List.of("a", "b"));
        List<String> copy = Checks.list(source, "xs");
        source.add("c");
        assertThat(copy).containsExactly("a", "b");
        assertThatThrownBy(() -> copy.add("d")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listsRefuseNullElementsAndRequiredListsRefuseNull() {
        assertThatThrownBy(() -> Checks.list(Arrays.asList("a", null), "xs")).hasMessage("xs must not contain null");
        assertThatThrownBy(() -> Checks.list(null, "xs")).hasMessage("xs is required");
        assertThat(Checks.optionalList(null, "xs")).isEmpty();
    }

    @Test
    void idListsCheckEveryElement() {
        assertThat(Checks.ids(List.of("a", "b_2"), "points")).hasSize(2);
        assertThatThrownBy(() -> Checks.ids(List.of("a", "2b"), "points")).hasMessageContaining("points[]");
    }

    @Test
    void sizedListsEnforceTheirBounds() {
        assertThat(Checks.sized(List.of(1, 2, 3), 3, 3, "polygon")).hasSize(3);
        assertThatThrownBy(() -> Checks.sized(List.of(1, 2), 3, 10, "polygon")).hasMessage("polygon must have 3 to 10 items, has 2");
    }

    @Test
    void rangesAndAllowedValues() {
        assertThat(Checks.between(5, 1, 5, "storeys")).isEqualTo(5);
        assertThatThrownBy(() -> Checks.between(Integer.valueOf(6), 1, 5, "storeys")).hasMessageContaining("between 1 and 5");
        assertThat(Checks.oneOf(3, "phases", 1, 3)).isEqualTo(3);
        assertThatThrownBy(() -> Checks.oneOf(2, "phases", 1, 3)).hasMessage("phases must be one of [1, 3], was 2");
        assertThatThrownBy(() -> Checks.positive(0, "width")).hasMessageContaining("greater than zero");
    }
}
