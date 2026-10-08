package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypex.electriplan.model.brief.AirConditioningType;
import com.hypex.electriplan.model.brief.CooktopType;
import com.hypex.electriplan.model.brief.HotWaterType;
import com.hypex.electriplan.model.brief.OvenType;
import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.model.common.Side;
import com.hypex.electriplan.model.common.Source;
import com.hypex.electriplan.model.design.CircuitType;
import com.hypex.electriplan.model.design.DeviceKind;
import com.hypex.electriplan.model.design.IcRating;
import com.hypex.electriplan.model.design.PointKind;
import com.hypex.electriplan.model.design.Severity;
import com.hypex.electriplan.model.design.SwitchWay;
import com.hypex.electriplan.model.design.TripCurve;
import com.hypex.electriplan.model.design.ZoneKind;
import com.hypex.electriplan.model.fixture.FixtureKind;
import com.hypex.electriplan.model.fixture.FixtureSource;
import com.hypex.electriplan.model.plan.DimensionUnit;
import com.hypex.electriplan.model.plan.DoorStyle;
import com.hypex.electriplan.model.plan.LabelKind;
import com.hypex.electriplan.model.plan.PlanItemSource;
import com.hypex.electriplan.model.plan.ScaleMethod;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every Java enum has exactly the values its schema lists, with the same JSON
 * names. Adding a value on one side only fails here.
 */
class EnumParityTest {

    static Stream<Arguments> enums() {
        return Stream.of(
                Arguments.of(Side.class, "common.schema.json", "/$defs/side/enum"),
                Arguments.of(Source.class, "common.schema.json", "/$defs/source/enum"),
                Arguments.of(AustralianState.class, "common.schema.json", "/$defs/australianState/enum"),
                Arguments.of(FixtureKind.class, "fixture.schema.json", "/properties/kind/enum"),
                Arguments.of(FixtureSource.class, "fixture.schema.json", "/properties/source/enum"),
                Arguments.of(CooktopType.class, "project-brief.schema.json", "/properties/appliances/properties/cooktop/enum"),
                Arguments.of(OvenType.class, "project-brief.schema.json", "/properties/appliances/properties/oven/enum"),
                Arguments.of(HotWaterType.class, "project-brief.schema.json", "/properties/appliances/properties/hotWater/enum"),
                Arguments.of(AirConditioningType.class, "project-brief.schema.json", "/properties/appliances/properties/airConditioning/enum"),
                Arguments.of(PointKind.class, "electrical-design.schema.json", "/$defs/designPoint/properties/kind/enum"),
                Arguments.of(IcRating.class, "electrical-design.schema.json", "/$defs/pointSpec/properties/ic/enum"),
                Arguments.of(SwitchWay.class, "electrical-design.schema.json", "/$defs/pointSpec/properties/ways/items/enum"),
                Arguments.of(ZoneKind.class, "electrical-design.schema.json", "/$defs/zone/properties/kind/enum"),
                Arguments.of(CircuitType.class, "electrical-design.schema.json", "/$defs/circuit/properties/type/enum"),
                Arguments.of(DeviceKind.class, "electrical-design.schema.json", "/$defs/switchboard/properties/devices/items/properties/kind/enum"),
                Arguments.of(TripCurve.class, "electrical-design.schema.json", "/$defs/switchboard/properties/devices/items/properties/curve/enum"),
                Arguments.of(Severity.class, "electrical-design.schema.json", "/$defs/violation/properties/severity/enum"),
                Arguments.of(PlanItemSource.class, "floor-plan.schema.json", "/$defs/planItemSource/enum"),
                Arguments.of(DoorStyle.class, "floor-plan.schema.json", "/$defs/door/properties/style/enum"),
                Arguments.of(LabelKind.class, "floor-plan.schema.json", "/$defs/label/properties/type/enum"),
                Arguments.of(DimensionUnit.class, "floor-plan.schema.json", "/$defs/dimension/properties/unit/enum"),
                Arguments.of(ScaleMethod.class, "floor-plan.schema.json", "/$defs/planSource/properties/scaleMethod/enum"));
    }

    @ParameterizedTest(name = "{0} matches {1}#{2}")
    @MethodSource("enums")
    void javaAndSchemaListTheSameValues(Class<? extends Enum<?>> type, String file, String pointer) {
        List<String> schema = new ArrayList<>();
        for (JsonNode value : Contracts.schemaNode(file, pointer)) {
            schema.add(value.asText());
        }
        List<String> java = Arrays.stream(type.getEnumConstants())
                .map(c -> ModelJson.write(c).replace("\"", ""))
                .toList();
        assertThat(java).containsExactlyElementsOf(schema);
    }

    @ParameterizedTest(name = "{0} reads back every value it writes")
    @MethodSource("enums")
    void everyValueRoundTrips(Class<? extends Enum<?>> type, String file, String pointer) {
        for (Enum<?> constant : type.getEnumConstants()) {
            assertThat(ModelJson.read(ModelJson.write(constant), type)).isSameAs(constant);
        }
    }
}
