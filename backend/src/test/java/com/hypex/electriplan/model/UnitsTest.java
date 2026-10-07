package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.DoubleFunction;
import java.util.stream.Stream;

import com.hypex.electriplan.model.units.Amperes;
import com.hypex.electriplan.model.units.Kilowatts;
import com.hypex.electriplan.model.units.Lumens;
import com.hypex.electriplan.model.units.Metres;
import com.hypex.electriplan.model.units.Millimetres;
import com.hypex.electriplan.model.units.Ohms;
import com.hypex.electriplan.model.units.Percent;
import com.hypex.electriplan.model.units.SquareMillimetres;
import com.hypex.electriplan.model.units.Watts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnitsTest {

    static Stream<Arguments> units() {
        return Stream.of(
                Arguments.of("Millimetres", (DoubleFunction<Object>) Millimetres::of, Millimetres.class),
                Arguments.of("Metres", (DoubleFunction<Object>) Metres::of, Metres.class),
                Arguments.of("Amperes", (DoubleFunction<Object>) Amperes::of, Amperes.class),
                Arguments.of("Ohms", (DoubleFunction<Object>) Ohms::of, Ohms.class),
                Arguments.of("SquareMillimetres", (DoubleFunction<Object>) SquareMillimetres::of, SquareMillimetres.class),
                Arguments.of("Watts", (DoubleFunction<Object>) Watts::of, Watts.class),
                Arguments.of("Kilowatts", (DoubleFunction<Object>) Kilowatts::of, Kilowatts.class),
                Arguments.of("Lumens", (DoubleFunction<Object>) Lumens::of, Lumens.class),
                Arguments.of("Percent", (DoubleFunction<Object>) Percent::of, Percent.class));
    }

    @ParameterizedTest(name = "{0} accepts zero and positive values")
    @MethodSource("units")
    void acceptsZeroAndPositive(String name, DoubleFunction<Object> of, Class<?> type) {
        assertThat(of.apply(0)).isNotNull();
        assertThat(of.apply(12.5)).isNotNull();
    }

    @ParameterizedTest(name = "{0} refuses negative, NaN and infinite values")
    @MethodSource("units")
    void refusesNegativeNanAndInfinity(String name, DoubleFunction<Object> of, Class<?> type) {
        assertThatThrownBy(() -> of.apply(-0.1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negative");
        assertThatThrownBy(() -> of.apply(Double.NaN)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("finite");
        assertThatThrownBy(() -> of.apply(Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} is a plain number in JSON, both ways")
    @MethodSource("units")
    void isAPlainJsonNumber(String name, DoubleFunction<Object> of, Class<?> type) throws Exception {
        Object value = of.apply(42.5);
        assertThat(ModelJson.write(value)).isEqualTo("42.5");
        assertThat(ModelJson.read("42.5", type)).isEqualTo(value);
        assertThat(ModelJson.read("42", type)).isEqualTo(of.apply(42));
    }

    @ParameterizedTest(name = "{0} refuses a number written as a string")
    @MethodSource("units")
    void refusesANumberWrittenAsAString(String name, DoubleFunction<Object> of, Class<?> type) {
        assertThatThrownBy(() -> ModelJson.read("\"42\"", type)).isInstanceOf(ModelJson.ModelJsonException.class);
    }

    @ParameterizedTest(name = "{0} refuses a negative value read from JSON")
    @MethodSource("units")
    void refusesNegativeFromJson(String name, DoubleFunction<Object> of, Class<?> type) {
        assertThatThrownBy(() -> ModelJson.read("-1", type))
                .isInstanceOf(ModelJson.ModelJsonException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void percentStopsAtOneHundred() {
        assertThat(Percent.of(100).value()).isEqualTo(100);
        assertThatThrownBy(() -> Percent.of(100.1)).hasMessageContaining("between 0.0 and 100.0");
    }

    @Test
    void unitsOfTheSameKindCompareByValue() {
        assertThat(Millimetres.of(300)).isLessThan(Millimetres.of(1100));
        assertThat(Amperes.of(20)).isEqualByComparingTo(Amperes.of(20.0));
        assertThat(Millimetres.of(5)).isEqualTo(new Millimetres(5));
    }
}
