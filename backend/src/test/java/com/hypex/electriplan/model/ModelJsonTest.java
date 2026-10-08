package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hypex.electriplan.model.brief.ProjectBrief;
import com.hypex.electriplan.model.common.Point;
import com.hypex.electriplan.model.design.ElectricalDesign;
import com.hypex.electriplan.model.design.PlanRef;
import com.hypex.electriplan.model.design.RulePackRef;
import org.junit.jupiter.api.Test;

/** The strict JSON rules: whatever the schemas would refuse, ModelJson refuses too. */
class ModelJsonTest {

    private static final String HASH = "sha256:" + "a".repeat(64);
    private static final String EMPTY_DESIGN = """
            {"version":1,"planRef":{"planHash":"%s","planVersion":1},
             "rulePack":{"id":"au-residential","version":"2026.1","state":"VIC","standards":[]},
             "points":[],"zones":[],"circuits":[],"violations":[],"decisionsRequired":[]}""".formatted(HASH);

    @Test
    void readsAValidDocument() {
        ElectricalDesign design = ModelJson.read(EMPTY_DESIGN, ElectricalDesign.class);
        assertThat(design.planRef().planVersion()).isEqualTo(1);
        assertThat(design.points()).isEmpty();
    }

    @Test
    void refusesUnknownProperties() {
        assertThatThrownBy(() -> ModelJson.read("{\"x\":1,\"y\":2,\"z\":3}", Point.class))
                .isInstanceOf(ModelJson.ModelJsonException.class)
                .hasMessageContaining("Not a valid Point")
                .hasMessageContaining("z");
    }

    @Test
    void refusesNumbersWrittenAsStrings() {
        assertThatThrownBy(() -> ModelJson.read("{\"x\":\"1\",\"y\":2}", Point.class)).isInstanceOf(ModelJson.ModelJsonException.class);
    }

    @Test
    void refusesAFractionWhereAnIntegerBelongs() {
        assertThatThrownBy(() -> ModelJson.read(EMPTY_DESIGN.replace("\"planVersion\":1", "\"planVersion\":1.5"), ElectricalDesign.class))
                .isInstanceOf(ModelJson.ModelJsonException.class);
    }

    @Test
    void refusesAnEnumGivenAsANumber() {
        String brief = """
                {"version":1,"state":1,"distributor":"citipower","supply":{"phases":1,"nominalVoltage":230},
                 "construction":{"storeys":1,"defaultCeilingHeight":2550,"ceilingInsulated":true,"roofSpaceAccessible":true,"slab":true},
                 "appliances":{"cooktop":"gas","oven":"electric","hotWater":"gas","airConditioning":"none","evCharger":false,"pool":false}}""";
        assertThatThrownBy(() -> ModelJson.read(brief, ProjectBrief.class)).isInstanceOf(ModelJson.ModelJsonException.class);
    }

    @Test
    void refusesAMissingRequiredField() {
        assertThatThrownBy(() -> ModelJson.read(EMPTY_DESIGN.replace("\"zones\":[],", ""), ElectricalDesign.class))
                .isInstanceOf(ModelJson.ModelJsonException.class)
                .hasMessageContaining("zones is required");
    }

    @Test
    void refusesTrailingContentAndDuplicateKeys() {
        assertThatThrownBy(() -> ModelJson.read("{\"x\":1,\"y\":2} {}", Point.class)).isInstanceOf(ModelJson.ModelJsonException.class);
        assertThatThrownBy(() -> ModelJson.read("{\"x\":1,\"x\":5,\"y\":2}", Point.class)).isInstanceOf(ModelJson.ModelJsonException.class);
    }

    @Test
    void refusesABooleanWrittenAsAString() {
        String brief = """
                {"version":1,"state":"VIC","distributor":"citipower","supply":{"phases":1,"nominalVoltage":230},
                 "construction":{"storeys":1,"defaultCeilingHeight":2550,"ceilingInsulated":"yes","roofSpaceAccessible":true,"slab":true},
                 "appliances":{"cooktop":"gas","oven":"electric","hotWater":"gas","airConditioning":"none","evCharger":false,"pool":false}}""";
        assertThatThrownBy(() -> ModelJson.read(brief, ProjectBrief.class)).isInstanceOf(ModelJson.ModelJsonException.class);
    }

    @Test
    void leavesOutAbsentOptionalValuesButKeepsRequiredEmptyLists() {
        ElectricalDesign design = ElectricalDesign.empty(new PlanRef(HASH, 1), new RulePackRef("au-residential", "2026.1", com.hypex.electriplan.model.common.AustralianState.VIC, java.util.List.of()));
        String json = ModelJson.write(design);
        assertThat(json).doesNotContain("null").doesNotContain("switchboard").doesNotContain("maxDemand");
        assertThat(json).contains("\"points\":[]").contains("\"violations\":[]").contains("\"decisionsRequired\":[]");
    }

    @Test
    void theMapperIsShared() {
        assertThat(ModelJson.mapper()).isSameAs(ModelJson.mapper());
    }
}
