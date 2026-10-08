package com.hypex.electriplan.design;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.hypex.electriplan.model.ModelJson;
import com.hypex.electriplan.model.Samples;
import com.hypex.electriplan.model.design.PlanRef;
import com.hypex.electriplan.model.plan.FloorPlan;
import com.hypex.electriplan.model.plan.FloorPlanReader;
import org.junit.jupiter.api.Test;

class PlanFingerprintTest {

    @Test
    void theHashIsASha256TheSchemaAccepts() {
        assertThat(PlanFingerprint.hash(Samples.floorPlan())).matches(PlanRef.HASH);
    }

    @Test
    void equalPlansHaveTheSameHashHoweverTheyWereMade() {
        FloorPlan built = Samples.floorPlan();
        FloorPlan read = ModelJson.read(ModelJson.write(built), FloorPlan.class);

        assertThat(PlanFingerprint.hash(read)).isEqualTo(PlanFingerprint.hash(built));
        assertThat(PlanFingerprint.hash(Samples.floorPlan())).isEqualTo(PlanFingerprint.hash(built));
    }

    @Test
    void anyChangeToThePlanChangesTheHash() {
        FloorPlan plan = Samples.floorPlan();
        FloorPlan moved = plan.withWalls(plan.walls().stream()
                .map(w -> w.id().equals("wall_001") ? Samples.wall("wall_001", 0, 0, 4001, 0) : w)
                .toList());

        assertThat(PlanFingerprint.hash(moved)).isNotEqualTo(PlanFingerprint.hash(plan));
    }

    @Test
    void theCanonicalJsonHasSortedKeysAndNoWhitespace() {
        String json = new String(PlanFingerprint.canonicalJson(Samples.floorPlan()), StandardCharsets.UTF_8);

        assertThat(json).doesNotContain("\n").doesNotContain(": ");
        assertThat(json.indexOf("\"dimensions\"")).isLessThan(json.indexOf("\"doors\""));
        assertThat(json.indexOf("\"units\"")).isLessThan(json.indexOf("\"version\""));
        assertThat(json.indexOf("\"version\"")).isLessThan(json.indexOf("\"walls\""));
    }

    @Test
    void theRefRecordsTheRevision() {
        PlanRef ref = PlanFingerprint.planRef(Samples.floorPlan(), 7);
        assertThat(ref.planVersion()).isEqualTo(7);
        assertThat(ref.planHash()).isEqualTo(PlanFingerprint.hash(Samples.floorPlan()));
    }

    @Test
    void aPlanReadThroughTheReaderHashesLikeTheSamePlanBuiltInJava() {
        FloorPlan read = FloorPlanReader.read(ModelJson.write(Samples.floorPlan()));
        assertThat(PlanFingerprint.hash(read)).isEqualTo(PlanFingerprint.hash(Samples.floorPlan()));
    }
}
