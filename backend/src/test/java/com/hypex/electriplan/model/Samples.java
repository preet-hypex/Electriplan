package com.hypex.electriplan.model;

import java.util.List;

import com.hypex.electriplan.model.brief.AirConditioningType;
import com.hypex.electriplan.model.brief.Appliances;
import com.hypex.electriplan.model.brief.Construction;
import com.hypex.electriplan.model.brief.CooktopType;
import com.hypex.electriplan.model.brief.DistributorCode;
import com.hypex.electriplan.model.brief.HotWaterType;
import com.hypex.electriplan.model.brief.OvenType;
import com.hypex.electriplan.model.brief.Preferences;
import com.hypex.electriplan.model.brief.ProjectBrief;
import com.hypex.electriplan.model.brief.Supply;
import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.model.common.Point;
import com.hypex.electriplan.model.common.Side;
import com.hypex.electriplan.model.common.Source;
import com.hypex.electriplan.model.common.WallAnchor;
import com.hypex.electriplan.model.design.CableSpec;
import com.hypex.electriplan.model.design.CeilingPlacement;
import com.hypex.electriplan.model.design.Circuit;
import com.hypex.electriplan.model.design.CircuitType;
import com.hypex.electriplan.model.design.ConsumerMains;
import com.hypex.electriplan.model.design.DecisionRequired;
import com.hypex.electriplan.model.design.DesignPoint;
import com.hypex.electriplan.model.design.DeviceKind;
import com.hypex.electriplan.model.design.ElectricalDesign;
import com.hypex.electriplan.model.design.IcRating;
import com.hypex.electriplan.model.design.MainSwitch;
import com.hypex.electriplan.model.design.MaxDemand;
import com.hypex.electriplan.model.design.PlanRef;
import com.hypex.electriplan.model.design.PointKind;
import com.hypex.electriplan.model.design.PointSpec;
import com.hypex.electriplan.model.design.ProtectiveDevice;
import com.hypex.electriplan.model.design.RouteSegment;
import com.hypex.electriplan.model.design.RulePackRef;
import com.hypex.electriplan.model.design.Severity;
import com.hypex.electriplan.model.design.SurgeProtection;
import com.hypex.electriplan.model.design.SwitchWay;
import com.hypex.electriplan.model.design.Switchboard;
import com.hypex.electriplan.model.design.TripCurve;
import com.hypex.electriplan.model.design.Violation;
import com.hypex.electriplan.model.design.WallPlacement;
import com.hypex.electriplan.model.design.Zone;
import com.hypex.electriplan.model.design.ZoneKind;
import com.hypex.electriplan.model.fixture.Fixture;
import com.hypex.electriplan.model.fixture.FixtureKind;
import com.hypex.electriplan.model.fixture.FixtureSource;
import com.hypex.electriplan.model.fixture.Footprint;
import com.hypex.electriplan.model.units.Amperes;
import com.hypex.electriplan.model.units.Kilowatts;
import com.hypex.electriplan.model.units.Lumens;
import com.hypex.electriplan.model.units.Metres;
import com.hypex.electriplan.model.units.Millimetres;
import com.hypex.electriplan.model.units.Ohms;
import com.hypex.electriplan.model.units.Percent;
import com.hypex.electriplan.model.units.SquareMillimetres;
import com.hypex.electriplan.model.units.Watts;

/** Model objects built in Java, the way engine code will build them. */
public final class Samples {

    public static final String HASH = "sha256:" + "0123456789abcdef".repeat(4);

    private Samples() {
    }

    public static ProjectBrief brief() {
        return ProjectBrief.builder()
                .state(AustralianState.VIC)
                .distributor(DistributorCode.of("jemena"))
                .supply(Supply.builder().phases(1).nominalVoltage(230).consumerMainsLengthM(Metres.of(15)).build())
                .construction(Construction.builder().storeys(1).defaultCeilingHeight(Millimetres.of(2550))
                        .ceilingInsulated(true).roofSpaceAccessible(true).slab(true).build())
                .appliances(Appliances.builder().cooktop(CooktopType.INDUCTION).oven(OvenType.ELECTRIC)
                        .hotWater(HotWaterType.HEAT_PUMP).airConditioning(AirConditioningType.SPLIT)
                        .evCharger(false).pool(false).build())
                .preferences(Preferences.builder().switchHeight(Millimetres.of(1100)).spareSwitchboardPolesPct(Percent.of(25)).build())
                .build();
    }

    public static Fixture shower() {
        return Fixture.builder()
                .id("shower_01").kind(FixtureKind.SHOWER).roomId("room_007")
                .footprint(Footprint.builder().centre(new Point(6450, 2100)).width(Millimetres.of(900))
                        .depth(Millimetres.of(900)).rotationDeg(0.0).build())
                .waterOutlet(new Point(6450, 1680))
                .source(FixtureSource.MANUAL)
                .build();
    }

    public static Fixture cooktop() {
        return Fixture.builder()
                .id("cooktop_01").kind(FixtureKind.COOKTOP)
                .footprint(Footprint.builder().centre(new Point(3200, 6100)).width(Millimetres.of(900))
                        .depth(Millimetres.of(520)).rotationDeg(90.0).build())
                .ratingKw(Kilowatts.of(7.2))
                .source(FixtureSource.VISION).confidence(0.8)
                .build();
    }

    public static DesignPoint downlight(String id, double x, double y) {
        return DesignPoint.builder()
                .id(id).kind(PointKind.DOWNLIGHT).roomId("room_002")
                .placement(new CeilingPlacement(new Point(x, y)))
                .spec(PointSpec.builder().itemCode("DL-IC4-10W-800LM").watts(Watts.of(10)).lumens(Lumens.of(800))
                        .ic(IcRating.IC_4).ip("IP20").build())
                .circuitId("c_L1").controlledBy("sw_001")
                .rationale("lumen method")
                .source(Source.ENGINE)
                .build();
    }

    public static DesignPoint switchFor(String... lights) {
        return DesignPoint.builder()
                .id("sw_001").kind(PointKind.SWITCH).roomId("room_002")
                .placement(WallPlacement.builder().wallId("wall_004").position(Millimetres.of(1460))
                        .side(Side.LEFT).height(Millimetres.of(1100)).build())
                .spec(PointSpec.builder().gangs(1).way(SwitchWay.ONE_WAY).build())
                .gang(List.of(lights))
                .rationale("latch side of door_001")
                .source(Source.ENGINE)
                .build();
    }

    /** A design with one of everything, built only through builders. */
    public static ElectricalDesign fullDesign() {
        return ElectricalDesign.builder()
                .planRef(new PlanRef(HASH, 2))
                .rulePack(RulePackRef.builder().id("au-residential").version("2026.1").state(AustralianState.VIC).standard("AS/NZS 3000:2018+A3").build())
                .point(downlight("lt_001", 1200, 1050))
                .point(downlight("lt_002", 3300, 1050))
                .point(switchFor("lt_001", "lt_002"))
                .zone(Zone.builder().id("z_001").kind(ZoneKind.BATH_ZONE_1).fixtureId("shower_01")
                        .corner(new Point(0, 0)).corner(new Point(1200, 0)).corner(new Point(1200, 1200))
                        .floorToHeight(Millimetres.of(2250)).rule("AS/NZS 3000 cl. 6.2.2").build())
                .circuit(Circuit.builder().id("c_L1").type(CircuitType.LIGHTING).label("Lights - Bed 2")
                        .point("lt_001").point("lt_002").protectionId("p_01").demandA(Amperes.of(0.2))
                        .cable(CableSpec.builder().csaMm2(SquareMillimetres.of(1.5)).type("TPS 2C+E").lengthM(Metres.of(14)).build())
                        .voltageDropPct(Percent.of(0.3)).zsOhm(Ohms.of(1.1)).zsMaxOhm(Ohms.of(7.28))
                        .segment(new RouteSegment("board", "lt_001", Metres.of(9)))
                        .build())
                .switchboard(Switchboard.builder()
                        .location(WallAnchor.builder().wallId("wall_031").position(Millimetres.of(600)).side(Side.LEFT)
                                .height(Millimetres.of(1500)).build())
                        .mainSwitch(MainSwitch.builder().ratingA(Amperes.of(63)).poles(1).build())
                        .device(ProtectiveDevice.builder().id("p_01").kind(DeviceKind.RCBO).ratingA(Amperes.of(10))
                                .curve(TripCurve.C).rcdMa(30.0).circuit("c_L1").build())
                        .spd(new SurgeProtection(true, "policy"))
                        .polesUsed(2).polesTotal(24)
                        .build())
                .maxDemand(MaxDemand.builder().method("AS/NZS 3000 Appendix C").phaseA(Amperes.of(41.5))
                        .consumerMains(new ConsumerMains(SquareMillimetres.of(16), "XLPE Cu")).build())
                .violation(Violation.builder().ruleId("policy.gpo.per-bedroom").severity(Severity.WARNING)
                        .itemId("lt_001").message("Only one double outlet in Bed 2").build())
                .decisionRequired(DecisionRequired.builder().id("d_001").itemId("lt_001").question("Ceiling height assumed 2550").build())
                .build();
    }
}
