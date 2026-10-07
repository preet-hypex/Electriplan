package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.hypex.electriplan.model.common.Point;
import com.hypex.electriplan.model.design.CableSpec;
import com.hypex.electriplan.model.design.CeilingPlacement;
import com.hypex.electriplan.model.design.Circuit;
import com.hypex.electriplan.model.design.DesignPoint;
import com.hypex.electriplan.model.design.ElectricalDesign;
import com.hypex.electriplan.model.design.MainSwitch;
import com.hypex.electriplan.model.design.MaxDemand;
import com.hypex.electriplan.model.design.PlanRef;
import com.hypex.electriplan.model.design.PointSpec;
import com.hypex.electriplan.model.design.ProtectiveDevice;
import com.hypex.electriplan.model.design.RulePackRef;
import com.hypex.electriplan.model.design.Severity;
import com.hypex.electriplan.model.design.Violation;
import com.hypex.electriplan.model.design.WallPlacement;
import com.hypex.electriplan.model.design.Zone;
import com.hypex.electriplan.model.units.Amperes;
import com.hypex.electriplan.model.units.Metres;
import com.hypex.electriplan.model.units.SquareMillimetres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DesignModelTest {

    private final ElectricalDesign design = Samples.fullDesign();

    @Test
    void anEmptyDesignIsWhereTheEngineStarts() {
        ElectricalDesign empty = ElectricalDesign.empty(new PlanRef(Samples.HASH, 1), design.rulePack());
        assertThat(empty.version()).isEqualTo(1);
        assertThat(empty.points()).isEmpty();
        assertThat(empty.switchboard()).isNull();
        assertThat(empty.hasErrors()).isFalse();
    }

    @Test
    void addingToADesignMakesANewOneAndLeavesTheOriginal() {
        DesignPoint extra = Samples.downlight("lt_003", 2000, 2000);
        ElectricalDesign more = design.toBuilder().point(extra).build();
        assertThat(more.points()).hasSize(design.points().size() + 1).contains(extra);
        assertThat(design.points()).doesNotContain(extra);
    }

    @Test
    void itsListsCannotBeChangedInPlace() {
        assertThatThrownBy(() -> design.points().add(Samples.downlight("lt_009", 0, 0))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> design.circuits().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void errorsAreMandatoryViolationsOnly() {
        assertThat(design.hasErrors()).isFalse();
        Violation error = new Violation("wet.gpo.zone2", Severity.ERROR, List.of("lt_001"), "In zone 2");
        assertThat(design.toBuilder().violation(error).build().hasErrors()).isTrue();
    }

    @Test
    void aDesignNeedsItsReferencesAndListsAndIsVersionOne() {
        assertThatThrownBy(() -> design.withPlanRef(null)).hasMessage("planRef is required");
        assertThatThrownBy(() -> design.withRulePack(null)).hasMessage("rulePack is required");
        assertThatThrownBy(() -> design.withPoints(null)).hasMessage("points is required");
        assertThatThrownBy(() -> design.withVersion(2)).hasMessageContaining("version");
    }

    @Test
    void planReferencesUseASha256Hash() {
        assertThatThrownBy(() -> new PlanRef("sha256:abc", 1)).hasMessageContaining("planHash");
        assertThatThrownBy(() -> new PlanRef("md5:" + "a".repeat(64), 1)).hasMessageContaining("planHash");
        assertThatThrownBy(() -> new PlanRef(Samples.HASH, 0)).hasMessageContaining("planVersion");
    }

    @Test
    void rulePackIdsAreLowercaseAndVersionsNotBlank() {
        assertThatThrownBy(() -> new RulePackRef("AU", "1", List.of())).hasMessageContaining("rulePack.id");
        assertThatThrownBy(() -> new RulePackRef("au", " ", List.of())).hasMessageContaining("rulePack.version");
        assertThatThrownBy(() -> new RulePackRef("au", "1", List.of(""))).hasMessageContaining("standards[]");
    }

    @Test
    void aPointNeedsAPlacementASpecAndASource() {
        DesignPoint point = design.points().getFirst();
        assertThatThrownBy(() -> point.withPlacement(null)).hasMessage("placement is required");
        assertThatThrownBy(() -> point.withSpec(null)).hasMessage("spec is required");
        assertThatThrownBy(() -> point.withSource(null)).hasMessage("source is required");
        assertThatThrownBy(() -> point.withRationale(null)).hasMessage("rationale is required");
        assertThatThrownBy(() -> point.withControlledBy(List.of("sw 1"))).hasMessageContaining("controlledBy");
        assertThatThrownBy(() -> point.withCircuitId("c L1")).hasMessageContaining("circuitId");
    }

    @Test
    void optionalPointListsDefaultToEmpty() {
        DesignPoint bare = new DesignPoint("gpo_001", com.hypex.electriplan.model.design.PointKind.GPO_DOUBLE, null,
                new CeilingPlacement(new Point(0, 0)), PointSpec.empty(), null, null, null, List.of(), com.hypex.electriplan.model.common.Source.MANUAL);
        assertThat(bare.controlledBy()).isEmpty();
        assertThat(bare.controls()).isEmpty();
    }

    @Test
    void aSwitchListsWhatEachGangControls() {
        DesignPoint sw = Samples.switchFor("lt_001", "lt_002");
        assertThat(sw.controls()).containsExactly(List.of("lt_001", "lt_002"));
        assertThatThrownBy(() -> sw.withControls(List.of(List.of("lt 1")))).hasMessageContaining("controls[]");
    }

    @Test
    void wallPlacementsNeedEveryCoordinate() {
        WallPlacement wall = (WallPlacement) Samples.switchFor("lt_001").placement();
        assertThatThrownBy(() -> wall.withHeight(null)).hasMessage("height is required");
        assertThatThrownBy(() -> wall.withSide(null)).hasMessage("side is required");
        assertThatThrownBy(() -> wall.withWallId("9")).hasMessageContaining("wallId");
        assertThatThrownBy(() -> new CeilingPlacement(null)).hasMessage("at is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"IP2", "ip44", "IP444", "XX44"})
    void ingressProtectionLooksLikeIp44(String ip) {
        assertThatThrownBy(() -> PointSpec.builder().ip(ip).build()).hasMessageContaining("ip");
    }

    @Test
    void specLimits() {
        assertThat(PointSpec.builder().ip("IPX4").gangs(6).build().gangs()).isEqualTo(6);
        assertThatThrownBy(() -> PointSpec.builder().gangs(7).build()).hasMessageContaining("gangs");
        assertThatThrownBy(() -> PointSpec.builder().ratingA(Amperes.of(0)).build()).hasMessageContaining("ratingA");
        assertThatThrownBy(() -> PointSpec.builder().itemCode("abc").build()).hasMessageContaining("itemCode");
    }

    @Test
    void aZoneIsAtLeastATriangle() {
        Zone zone = design.zones().getFirst();
        assertThatThrownBy(() -> zone.withPolygon(zone.polygon().subList(0, 2))).hasMessageContaining("polygon must have 3");
        assertThatThrownBy(() -> zone.withRule("")).hasMessage("rule must not be blank");
        assertThatThrownBy(() -> zone.withFixtureId(null)).hasMessage("fixtureId is required");
    }

    @Test
    void circuitRules() {
        Circuit circuit = design.circuits().getFirst();
        assertThatThrownBy(() -> circuit.withLabel(" ")).hasMessage("label must not be blank");
        assertThatThrownBy(() -> circuit.withPoints(List.of("x y"))).hasMessageContaining("points[]");
        assertThat(circuit.withRoute(null).route()).isEmpty();
        assertThatThrownBy(() -> new CableSpec(SquareMillimetres.of(0), "TPS", Metres.of(1))).hasMessageContaining("csaMm2");
        assertThatThrownBy(() -> new CableSpec(SquareMillimetres.of(1.5), "", Metres.of(1))).hasMessageContaining("cable.type");
    }

    @Test
    void switchboardRules() {
        assertThatThrownBy(() -> new MainSwitch(Amperes.of(63), 2)).hasMessageContaining("poles");
        assertThatThrownBy(() -> new MainSwitch(Amperes.of(0), 1)).hasMessageContaining("ratingA");
        ProtectiveDevice rcbo = design.switchboard().devices().getFirst();
        assertThatThrownBy(() -> rcbo.withRcdMa(0.0)).hasMessageContaining("rcdMa");
        assertThatThrownBy(() -> rcbo.withKind(null)).hasMessage("kind is required");
        assertThatThrownBy(() -> design.switchboard().withPolesUsed(-1)).hasMessageContaining("polesUsed");
    }

    @Test
    void maxDemandHasOneToThreePhases() {
        MaxDemand demand = design.maxDemand();
        assertThatThrownBy(() -> demand.withPerPhaseA(List.of())).hasMessageContaining("perPhaseA must have 1 to 3");
        assertThatThrownBy(() -> demand.withPerPhaseA(List.of(Amperes.of(1), Amperes.of(1), Amperes.of(1), Amperes.of(1))))
                .hasMessageContaining("perPhaseA must have 1 to 3");
    }

    @Test
    void violationsUseDottedRuleIds() {
        assertThat(new Violation("wet.gpo.zone-2", Severity.ERROR, List.of(), "x").ruleId()).isEqualTo("wet.gpo.zone-2");
        assertThatThrownBy(() -> new Violation("Wet.GPO", Severity.ERROR, List.of(), "x")).hasMessageContaining("ruleId");
        assertThatThrownBy(() -> new Violation("wet.gpo", null, List.of(), "x")).hasMessage("severity is required");
        assertThatThrownBy(() -> new Violation("wet.gpo", Severity.ERROR, List.of(), "")).hasMessage("message must not be blank");
    }
}
