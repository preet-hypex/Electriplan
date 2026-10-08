package com.hypex.electriplan.projects.service;

import static com.hypex.electriplan.projects.domain.HouseStage.ANALYSING;
import static com.hypex.electriplan.projects.domain.HouseStage.AWAITING_UPLOAD;
import static com.hypex.electriplan.projects.domain.HouseStage.ELECTRICAL_DESIGN;
import static com.hypex.electriplan.projects.domain.HouseStage.FLOOR_PLAN_APPROVED;
import static com.hypex.electriplan.projects.domain.HouseStage.FLOOR_PLAN_REVIEW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hypex.electriplan.projects.dao.HouseRepository;
import com.hypex.electriplan.projects.dao.StageTransitionRepository;
import com.hypex.electriplan.projects.domain.HouseStage;
import com.hypex.electriplan.projects.domain.ProjectsProblem;
import com.hypex.electriplan.projects.entity.HouseEntity;
import com.hypex.electriplan.projects.entity.StageTransitionEntity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Moves a house's stage as its floor-plan work happens (P7):
 *
 * <table>
 *   <tr><td>an image is being analysed</td><td>→ analysing</td></tr>
 *   <tr><td>the plan changes (analysed, drawn, imported, edited, restored)</td><td>→ floor_plan_review</td></tr>
 *   <tr><td>the analysis fails</td><td>→ back to awaiting_upload, or floor_plan_review if the house has a plan</td></tr>
 *   <tr><td>the floor plan is approved</td><td>→ floor_plan_approved</td></tr>
 * </table>
 *
 * Only houses in the floor-plan stages (and electrical design, which a floor
 * plan change sends back for checking) follow the floor plan; a house in
 * review, quoting or won stays where it is. A move goes along the shortest
 * path the workflow allows (electriplan.plan_stage_transition, which the
 * database enforces), one step at a time, so each step is in the house's
 * history with who made it.
 */
@Service
@RequiredArgsConstructor
public class HouseStages {

    /** The stages that follow the floor plan's work. */
    static final Set<HouseStage> FOLLOW_THE_FLOOR_PLAN =
            Collections.unmodifiableSet(EnumSet.of(AWAITING_UPLOAD, ANALYSING, FLOOR_PLAN_REVIEW, FLOOR_PLAN_APPROVED, ELECTRICAL_DESIGN));

    private final HouseRepository houses;
    private final StageTransitionRepository transitions;

    public void analysisStarted(HouseEntity house) {
        moveTo(house, ANALYSING);
    }

    /** The plan changed: it needs checking (again). */
    public void floorPlanChanged(HouseEntity house) {
        moveTo(house, FLOOR_PLAN_REVIEW);
    }

    public void analysisFailed(HouseEntity house, boolean hasFloorPlan) {
        if (house.getStage() == ANALYSING) {
            moveTo(house, hasFloorPlan ? FLOOR_PLAN_REVIEW : AWAITING_UPLOAD);
        }
    }

    /** Only a floor plan being checked can be approved (an approved one already is). */
    public void approved(HouseEntity house) {
        if (house.getStage() == FLOOR_PLAN_APPROVED) {
            return;
        }
        if (house.getStage() != FLOOR_PLAN_REVIEW) {
            throw ProjectsProblem.conflict("Only a floor plan being checked can be approved; this house is at "
                    + house.getStage().code().replace('_', ' ') + ".");
        }
        moveTo(house, FLOOR_PLAN_APPROVED);
    }

    private void moveTo(HouseEntity house, HouseStage target) {
        if (house.getStage() == target || !FOLLOW_THE_FLOOR_PLAN.contains(house.getStage())) {
            return;
        }
        for (HouseStage step : path(house.getStage(), target)) {
            house.moveTo(step);
            houses.saveAndFlush(house); // one row per step: the database records each move
        }
    }

    /** The shortest way from one stage to another, staying among the floor-plan stages; none if there is no way. */
    List<HouseStage> path(HouseStage from, HouseStage to) {
        Map<HouseStage, List<HouseStage>> next = new EnumMap<>(HouseStage.class);
        for (StageTransitionEntity t : transitions.findAll()) {
            HouseStage a = t.getMove().getFrom();
            HouseStage b = t.getMove().getTo();
            if (FOLLOW_THE_FLOOR_PLAN.contains(a) && FOLLOW_THE_FLOOR_PLAN.contains(b)) {
                next.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
            }
        }
        Map<HouseStage, HouseStage> cameFrom = new HashMap<>();
        Deque<HouseStage> queue = new ArrayDeque<>(List.of(from));
        while (!queue.isEmpty()) {
            HouseStage at = queue.removeFirst();
            if (at == to) {
                List<HouseStage> steps = new ArrayList<>();
                for (HouseStage s = to; s != from; s = cameFrom.get(s)) {
                    steps.add(0, s);
                }
                return steps;
            }
            for (HouseStage n : next.getOrDefault(at, List.of())) {
                if (n != from && cameFrom.putIfAbsent(n, at) == null) {
                    queue.addLast(n);
                }
            }
        }
        return List.of();
    }
}
