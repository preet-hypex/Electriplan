package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hypex.electriplan.model.common.Point;
import com.hypex.electriplan.model.common.Side;
import com.hypex.electriplan.model.common.WallAnchor;
import com.hypex.electriplan.model.fixture.Fixture;
import com.hypex.electriplan.model.fixture.Footprint;
import com.hypex.electriplan.model.units.Kilowatts;
import com.hypex.electriplan.model.units.Millimetres;
import org.junit.jupiter.api.Test;

class FixtureModelTest {

    private final Fixture shower = Samples.shower();

    @Test
    void aFixtureNeedsAnIdKindFootprintAndSource() {
        assertThatThrownBy(() -> shower.withId("1st")).hasMessageContaining("id");
        assertThatThrownBy(() -> shower.withKind(null)).hasMessage("kind is required");
        assertThatThrownBy(() -> shower.withFootprint(null)).hasMessage("footprint is required");
        assertThatThrownBy(() -> shower.withSource(null)).hasMessage("source is required");
        assertThatThrownBy(() -> shower.withRoomId("bad room")).hasMessageContaining("roomId");
    }

    @Test
    void aFootprintHasSizeAndARotationWithinOneTurn() {
        Footprint f = shower.footprint();
        assertThatThrownBy(() -> f.withWidth(Millimetres.of(0))).hasMessageContaining("width must be greater than zero");
        assertThatThrownBy(() -> f.withDepth(Millimetres.of(0))).hasMessageContaining("depth must be greater than zero");
        assertThatThrownBy(() -> f.withRotationDeg(361.0)).hasMessageContaining("rotationDeg");
        assertThat(f.withRotationDeg(-360.0).rotationDeg()).isEqualTo(-360.0);
        assertThatThrownBy(() -> f.withCentre(null)).hasMessage("centre is required");
    }

    @Test
    void confidenceIsAFraction() {
        assertThat(shower.withConfidence(0.0).confidence()).isZero();
        assertThat(shower.withConfidence(1.0).confidence()).isEqualTo(1.0);
        assertThatThrownBy(() -> shower.withConfidence(1.01)).hasMessageContaining("confidence");
        assertThatThrownBy(() -> shower.withConfidence(-0.1)).hasMessageContaining("confidence");
    }

    @Test
    void anApplianceRatingIsAboveZero() {
        assertThat(Samples.cooktop().ratingKw()).isEqualTo(Kilowatts.of(7.2));
        assertThatThrownBy(() -> Samples.cooktop().withRatingKw(Kilowatts.of(0))).hasMessageContaining("ratingKw");
    }

    @Test
    void aWallFixtureMovesWithItsWall() {
        WallAnchor anchor = new WallAnchor("wall_012", Millimetres.of(1800), Side.RIGHT, Millimetres.of(900));
        Fixture bench = shower.withWallAnchor(anchor);
        assertThat(bench.wallAnchor().wallId()).isEqualTo("wall_012");
        assertThatThrownBy(() -> anchor.withWallId("")).hasMessageContaining("wallId");
        assertThatThrownBy(() -> anchor.withSide(null)).hasMessage("side is required");
        assertThat(anchor.withHeight(null).height()).isNull();
    }

    @Test
    void plansCoordinatesMayBeNegativeButMustBeNumbers() {
        assertThat(new Point(-120, -5).x()).isEqualTo(-120);
        assertThatThrownBy(() -> new Point(Double.NaN, 0)).hasMessageContaining("x must be a finite number");
    }
}
