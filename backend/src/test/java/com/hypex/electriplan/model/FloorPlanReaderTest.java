package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hypex.electriplan.model.Contracts.Document;
import com.hypex.electriplan.model.Contracts.Example;
import com.hypex.electriplan.model.plan.DoorStyle;
import com.hypex.electriplan.model.plan.FloorPlan;
import com.hypex.electriplan.model.plan.FloorPlanReader;
import com.hypex.electriplan.model.plan.InvalidFloorPlanException;
import com.hypex.electriplan.model.plan.ScaleMethod;
import com.hypex.electriplan.model.plan.UnsupportedFloorPlanVersionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** How a floor plan comes into the engine: valid plans become records; anything else is refused with a reason. */
class FloorPlanReaderTest {

    static Stream<Example> validPlans() {
        return Contracts.examples(Document.FLOOR_PLAN, true).stream();
    }

    static Stream<Example> invalidPlans() {
        return Contracts.examples(Document.FLOOR_PLAN, false).stream();
    }

    private static String example(String file) {
        return Contracts.examples(Document.FLOOR_PLAN, true).stream()
                .filter(e -> e.file().equals(file))
                .findFirst().orElseThrow().json();
    }

    @ParameterizedTest(name = "reads {0}")
    @MethodSource("validPlans")
    void readsEveryValidExample(Example example) {
        FloorPlan plan = FloorPlanReader.read(example.json());
        assertThat(plan.version()).isEqualTo(1);
        assertThat(plan).isEqualTo(ModelJson.read(example.json(), FloorPlan.class));
    }

    @ParameterizedTest(name = "refuses {0}")
    @MethodSource("invalidPlans")
    void refusesEveryInvalidExample(Example example) {
        assertThatThrownBy(() -> FloorPlanReader.read(example.json()))
                .isInstanceOf(InvalidFloorPlanException.class)
                .satisfies(e -> assertThat(((InvalidFloorPlanException) e).problems()).isNotEmpty());
    }

    @Test
    void readsWhatTheEngineNeedsFromAnAnalysedPlan() {
        FloorPlan plan = FloorPlanReader.read(example("analysed-two-bedroom-unit.json"));
        assertThat(plan.walls()).hasSize(6);
        assertThat(plan.rooms()).extracting(r -> r.name()).contains("BED 1", "BED 2", "LIVING", "");
        assertThat(plan.doors()).extracting(d -> d.effectiveStyle())
                .containsExactly(DoorStyle.SWING, DoorStyle.SWING, DoorStyle.SLIDING);
        assertThat(plan.doors().get(1).swing()).isEqualTo(-90);
        assertThat(plan.source().scaleMethod()).isEqualTo(ScaleMethod.OCR_DIMENSIONS);
        assertThat(plan.source().scaleConfidence()).isEqualTo(0.82);
    }

    @ParameterizedTest(name = "version {0} is refused as unsupported")
    @ValueSource(ints = {0, 2, 99, -1})
    void refusesAVersionItDoesNotRead(int version) {
        String json = withVersion(version);
        assertThatThrownBy(() -> FloorPlanReader.read(json))
                .isInstanceOf(UnsupportedFloorPlanVersionException.class)
                .hasMessageContaining("version " + version + " is not supported")
                .hasMessageContaining("reads version 1")
                .satisfies(e -> {
                    UnsupportedFloorPlanVersionException unsupported = (UnsupportedFloorPlanVersionException) e;
                    assertThat(unsupported.version()).isEqualTo(version);
                    assertThat(unsupported.supportedVersions()).containsExactly(1);
                });
    }

    @Test
    void theUnsupportedVersionExampleIsRefusedForItsVersion() {
        Example example = Contracts.examples(Document.FLOOR_PLAN, false).stream()
                .filter(e -> e.file().equals("unsupported-version.json")).findFirst().orElseThrow();
        assertThatThrownBy(() -> FloorPlanReader.read(example.json()))
                .isInstanceOf(UnsupportedFloorPlanVersionException.class);
    }

    @Test
    void aMissingOrMalformedVersionIsInvalidNotUnsupported() {
        ObjectNode plan = (ObjectNode) Contracts.tree(example("empty-plan.json"));
        plan.remove("version");
        assertThatThrownBy(() -> FloorPlanReader.read(plan.toString()))
                .isInstanceOf(InvalidFloorPlanException.class)
                .isNotInstanceOf(UnsupportedFloorPlanVersionException.class)
                .hasMessageContaining("version");

        plan.put("version", "1");
        assertThatThrownBy(() -> FloorPlanReader.read(plan.toString()))
                .isInstanceOf(InvalidFloorPlanException.class)
                .isNotInstanceOf(UnsupportedFloorPlanVersionException.class);
    }

    @Test
    void reportsEverySchemaProblemAtOnce() {
        String json = example("analysed-two-bedroom-unit.json")
                .replace("\"thickness\": 80, \"confidence\": 0.94", "\"thickness\": 0, \"confidence\": 0.94")
                .replace("\"swing\": -90", "\"swing\": 45");
        assertThatThrownBy(() -> FloorPlanReader.read(json))
                .isInstanceOf(InvalidFloorPlanException.class)
                .satisfies(e -> assertThat(((InvalidFloorPlanException) e).problems())
                        .anyMatch(p -> p.contains("walls[0].thickness"))
                        .anyMatch(p -> p.contains("doors[1].swing")));
    }

    @Test
    void refusesWhatIsNotAPlanAtAll() {
        assertThatThrownBy(() -> FloorPlanReader.read("{not json")).isInstanceOf(InvalidFloorPlanException.class)
                .hasMessageContaining("not JSON");
        assertThatThrownBy(() -> FloorPlanReader.read("[1, 2]")).isInstanceOf(InvalidFloorPlanException.class)
                .hasMessageContaining("expected a JSON object");
        assertThatThrownBy(() -> FloorPlanReader.read("{\"version\":1,\"version\":2}"))
                .isInstanceOf(InvalidFloorPlanException.class);
    }

    @Test
    void whatJavaBuildsReadsBack() {
        assertThat(FloorPlanReader.read(ModelJson.write(Samples.floorPlan()))).isEqualTo(Samples.floorPlan());
    }

    private static String withVersion(int version) {
        ObjectNode plan = (ObjectNode) Contracts.tree(example("empty-plan.json"));
        plan.put("version", version);
        return plan.toString();
    }
}
