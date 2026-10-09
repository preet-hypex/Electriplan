package com.hypex.electriplan.projects.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypex.electriplan.model.ModelJson;
import com.hypex.electriplan.model.common.Point;
import com.hypex.electriplan.model.plan.FloorPlan;
import com.hypex.electriplan.model.plan.FloorPlanReader;
import com.hypex.electriplan.model.plan.InvalidFloorPlanException;
import com.hypex.electriplan.model.plan.PlanSource;
import com.hypex.electriplan.projects.domain.ProjectsProblem;
import com.hypex.electriplan.projects.dto.FieldProblem;
import com.hypex.electriplan.projects.entity.FloorPlanFigures;

import org.jspecify.annotations.Nullable;

/**
 * A floor plan as sent by the editor: checked against the contract, written
 * back in the model's own JSON, and measured for the figures lists show.
 */
final class FloorPlanReading {

    /** Below this, a detected item (or the scale) still needs a person to check it, as in the editor. */
    static final double CONFIDENT = 0.6;

    private FloorPlanReading() {
    }

    /** The plan, checked; 400 naming every problem otherwise. */
    static FloorPlan read(JsonNode document) {
        try {
            return FloorPlanReader.read(document);
        } catch (InvalidFloorPlanException e) {
            throw ProjectsProblem.invalid(e.problems().stream().map(p -> new FieldProblem("document", p)).toList());
        }
    }

    static String json(FloorPlan plan) {
        return ModelJson.write(plan);
    }

    static FloorPlanFigures figures(FloorPlan plan, String json) {
        PlanSource source = plan.source();
        return new FloorPlanFigures(
                plan.rooms().size(),
                plan.walls().size(),
                plan.doors().size() + plan.windows().size() + plan.openings().size(),
                floorArea(plan),
                openChecks(plan),
                source == null ? null : BigDecimal.valueOf(source.mmPerPx()).setScale(4, RoundingMode.HALF_UP),
                source == null || source.scaleConfidence() == null ? null
                        : BigDecimal.valueOf(source.scaleConfidence()).setScale(3, RoundingMode.HALF_UP),
                source == null || source.scaleMethod() == null ? null : ModelJson.write(source.scaleMethod()).replace("\"", ""),
                sha256(json));
    }

    /** The rooms' areas added up, in square metres (shoelace formula; plan coordinates are millimetres). */
    static BigDecimal floorArea(FloorPlan plan) {
        double mm2 = plan.rooms().stream().mapToDouble(room -> area(room.polygon())).sum();
        return BigDecimal.valueOf(mm2 / 1_000_000).setScale(2, RoundingMode.HALF_UP);
    }

    private static double area(List<Point> polygon) {
        double twice = 0;
        for (int i = 0; i < polygon.size(); i++) {
            Point a = polygon.get(i);
            Point b = polygon.get((i + 1) % polygon.size());
            twice += a.x() * b.y() - b.x() * a.y();
        }
        return Math.abs(twice) / 2;
    }

    /** Detected items the analyser was not sure of, and the scale if it is doubtful. */
    static int openChecks(FloorPlan plan) {
        long unsure = Stream.of(
                        plan.walls().stream().map(w -> w.confidence()),
                        plan.rooms().stream().map(r -> r.confidence()),
                        plan.doors().stream().map(d -> d.confidence()),
                        plan.windows().stream().map(w -> w.confidence()),
                        plan.openings().stream().map(o -> o.confidence()))
                .flatMap(s -> s)
                .filter(c -> c != null && c < CONFIDENT)
                .count();
        Double scale = plan.source() == null ? null : plan.source().scaleConfidence();
        return (int) unsure + (scale != null && scale < CONFIDENT ? 1 : 0);
    }

    private static byte[] sha256(String json) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    static @Nullable JsonNode tree(String json) {
        try {
            return ModelJson.mapper().readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("A stored floor plan is not JSON", e);
        }
    }
}
