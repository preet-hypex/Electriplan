package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.hypex.electriplan.model.common.Point;
import com.hypex.electriplan.model.plan.AnalysisReport;
import com.hypex.electriplan.model.plan.Dimension;
import com.hypex.electriplan.model.plan.Door;
import com.hypex.electriplan.model.plan.DoorStyle;
import com.hypex.electriplan.model.plan.FloorPlan;
import com.hypex.electriplan.model.plan.PlanSource;
import com.hypex.electriplan.model.plan.Room;
import com.hypex.electriplan.model.plan.Wall;
import com.hypex.electriplan.model.units.Millimetres;
import org.junit.jupiter.api.Test;

/** The floor plan's records keep the schema's rules: a plan that exists is a valid plan. */
class PlanModelTest {

    private final FloorPlan plan = Samples.floorPlan();

    @Test
    void aPlanIsVersionOneInMillimetres() {
        assertThat(FloorPlan.builder().build().version()).isEqualTo(FloorPlan.VERSION);
        assertThat(FloorPlan.builder().build().units()).isEqualTo("mm");
        assertThatThrownBy(() -> plan.withVersion(2)).hasMessageContaining("version must be one of [1]");
        assertThatThrownBy(() -> plan.withUnits("ft")).hasMessageContaining("units must be \"mm\"");
        assertThatThrownBy(() -> plan.withWalls(null)).hasMessage("walls is required");
    }

    @Test
    void aWallHasAnIdAndAThickness() {
        Wall wall = plan.walls().get(0);
        assertThatThrownBy(() -> wall.withId("wall 1")).hasMessageContaining("id");
        assertThatThrownBy(() -> wall.withThickness(Millimetres.of(0))).hasMessageContaining("thickness must be greater than zero");
        assertThatThrownBy(() -> wall.withStart(null)).hasMessage("start is required");
        assertThatThrownBy(() -> wall.withConfidence(1.5)).hasMessageContaining("confidence");
        assertThat(wall.withConfidence(null).withSource(null).confidence()).isNull();
    }

    @Test
    void aRoomHasThreeCornersAndMayBeUnnamed() {
        Room room = plan.rooms().get(0);
        assertThat(room.withName("").name()).isEmpty();
        assertThatThrownBy(() -> room.withName(null)).hasMessage("name is required");
        assertThatThrownBy(() -> room.withPolygon(List.of(new Point(0, 0), new Point(1, 1))))
                .hasMessageContaining("polygon must have 3 to");
        assertThat(room.withColour("#10b981").colour()).isEqualTo("#10b981");
        assertThatThrownBy(() -> room.withColour("green")).hasMessageContaining("colour");
    }

    @Test
    void aDoorSitsInAWallAndOpensToOneSide() {
        Door door = plan.doors().get(0);
        assertThat(door.withSwing(Door.RIGHT).swing()).isEqualTo(-90);
        assertThatThrownBy(() -> door.withSwing(45.0)).hasMessageContaining("swing must be 90 or -90");
        assertThatThrownBy(() -> door.withWidth(Millimetres.of(0))).hasMessageContaining("width must be greater than zero");
        assertThatThrownBy(() -> door.withWallId("")).hasMessageContaining("wallId");
        assertThat(door.withStyle(null).effectiveStyle()).isEqualTo(DoorStyle.SWING);
        assertThat(door.withStyle(DoorStyle.GARAGE).effectiveStyle()).isEqualTo(DoorStyle.GARAGE);
    }

    @Test
    void aDimensionHasAPositiveValueAndAUnit() {
        Dimension dimension = plan.dimensions().get(0);
        assertThatThrownBy(() -> dimension.withValue(0.0)).hasMessageContaining("value must be greater than zero");
        assertThatThrownBy(() -> dimension.withUnit(null)).hasMessage("unit is required");
    }

    @Test
    void aSourceImageHasASizeAScaleAndAConfidence() {
        PlanSource source = plan.source();
        assertThatThrownBy(() -> source.withImageUrl("")).hasMessageContaining("imageUrl");
        assertThatThrownBy(() -> source.withImageWidth(0)).hasMessageContaining("imageWidth");
        assertThatThrownBy(() -> source.withMmPerPx(0.0)).hasMessageContaining("mmPerPx");
        assertThatThrownBy(() -> source.withScaleConfidence(1.1)).hasMessageContaining("scaleConfidence");
        assertThatThrownBy(() -> source.planRegion().withWidth(0.0)).hasMessageContaining("planRegion.width");
    }

    @Test
    void anAnalysisReportCountsFromZero() {
        AnalysisReport report = plan.analysis();
        assertThatThrownBy(() -> report.withWallCount(-1)).hasMessageContaining("wallCount");
        assertThatThrownBy(() -> new AnalysisReport.Step("", true, "")).hasMessageContaining("name");
    }

    @Test
    void listsAreImmutable() {
        assertThatThrownBy(() -> plan.walls().add(plan.walls().get(0))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.rooms().get(0).polygon().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
