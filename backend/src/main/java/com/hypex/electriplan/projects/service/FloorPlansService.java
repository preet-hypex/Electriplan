package com.hypex.electriplan.projects.service;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypex.electriplan.model.plan.FloorPlan;
import com.hypex.electriplan.projects.dao.FloorPlanVersionRepository;
import com.hypex.electriplan.projects.dao.HouseRepository;
import com.hypex.electriplan.projects.dao.LevelRepository;
import com.hypex.electriplan.projects.dao.ProjectRepository;
import com.hypex.electriplan.projects.domain.FloorPlanOrigin;
import com.hypex.electriplan.projects.domain.FloorPlanState;
import com.hypex.electriplan.projects.domain.ProjectsProblem;
import com.hypex.electriplan.projects.dto.CommitForm;
import com.hypex.electriplan.projects.dto.FloorPlanDocument;
import com.hypex.electriplan.projects.dto.FloorPlanVersion;
import com.hypex.electriplan.projects.dto.RestoreForm;
import com.hypex.electriplan.projects.dto.SaveDraftForm;
import com.hypex.electriplan.projects.entity.FloorPlanVersionEntity;
import com.hypex.electriplan.projects.entity.HouseEntity;
import com.hypex.electriplan.projects.entity.LevelEntity;
import com.hypex.electriplan.projects.entity.ProjectEntity;
import com.hypex.electriplan.tenancy.domain.CompanyContext;
import com.hypex.electriplan.tenancy.service.CurrentCompany;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A house's floor plan, kept as the editor works: one draft that saves itself,
 * and numbered versions frozen with "Save version" (P4). v1 houses have one
 * storey, the ground floor, so a house's floor plan is that storey's.
 *
 * <ul>
 *   <li>Open: the draft if there is one, otherwise the newest version.</li>
 *   <li>Save the draft: creates it (numbered after the last version) or
 *       replaces its contents, refusing a stale save (409).</li>
 *   <li>Save a version: freezes the draft; it becomes the storey's current
 *       floor plan, and the next edit starts a new draft from it.</li>
 *   <li>Restore: an earlier version's contents become the draft.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FloorPlansService {

    private final ProjectRepository projects;
    private final HouseRepository houses;
    private final LevelRepository levels;
    private final FloorPlanVersionRepository versions;
    private final CurrentCompany current;

    /** What the editor opens: the draft, or the newest version; 404 when the house has no floor plan yet. */
    @Transactional(readOnly = true)
    public FloorPlanDocument open(UUID houseId) {
        LevelEntity level = groundFloor(houses.require(houseId));
        FloorPlanVersionEntity shown = versions.findDraft(level.getId())
                .or(() -> versions.findFirstByLevelIdAndStateOrderByVersionNoDesc(level.getId(), FloorPlanState.COMMITTED))
                .orElseThrow(() -> ProjectsProblem.missing("This house has no floor plan yet."));
        return document(houseId, shown);
    }

    /** The editor's plan as the house's draft: a new draft, or new contents for the one there is. */
    public FloorPlanDocument saveDraft(UUID houseId, SaveDraftForm form) {
        FloorPlanVersionEntity draft = writeDraft(houseId, FloorPlanReading.read(form.document()), form.version(),
                form.origin() == null ? FloorPlanOrigin.EDITOR : form.origin());
        return document(houseId, versions.saveAndFlush(draft));
    }

    /** The analyser's plan of an uploaded image as the house's draft, recording the run and the image. */
    public FloorPlanDocument saveAnalysed(UUID houseId, JsonNode document, @Nullable Integer version, UUID runId, UUID fileId) {
        FloorPlanVersionEntity draft = writeDraft(houseId, FloorPlanReading.read(document), version, FloorPlanOrigin.ANALYSIS);
        draft.analysedFrom(runId, fileId);
        return document(houseId, versions.saveAndFlush(draft));
    }

    /**
     * The house's draft with the plan in it: a new draft (numbered after the
     * last version) when there is none, otherwise the draft there is, if
     * {@code version} is the one it has.
     */
    private FloorPlanVersionEntity writeDraft(UUID houseId, FloorPlan plan, @Nullable Integer version, FloorPlanOrigin origin) {
        HouseEntity house = editableHouse(houseId);
        LevelEntity level = groundFloor(house);
        String json = FloorPlanReading.json(plan);

        FloorPlanVersionEntity draft = versions.findDraft(level.getId()).orElse(null);
        if (draft == null) {
            if (version != null) {
                // The caller was editing a draft that has since been saved as a version.
                throw ProjectsProblem.conflict("This floor plan was saved as a version since you opened it. Reload to continue from it.");
            }
            draft = newDraft(level, origin, latestCommittedId(level));
        } else {
            EditVersion.requireUnchanged(version, draft.getLockVersion());
        }
        draft.replaceDocument(json, FloorPlanReading.figures(plan, json));
        return draft;
    }

    /** The storey a house's floor plan belongs to (v1: its ground floor), if the house may be changed. */
    public UUID editableLevel(UUID houseId) {
        return groundFloor(editableHouse(houseId)).getId();
    }

    /** The draft, frozen as a numbered version: from now on the storey's floor plan. */
    public FloorPlanVersion commit(UUID houseId, CommitForm form) {
        HouseEntity house = editableHouse(houseId);
        LevelEntity level = groundFloor(house);
        FloorPlanVersionEntity draft = versions.findDraft(level.getId())
                .orElseThrow(() -> ProjectsProblem.conflict("There are no unsaved changes to save as a version."));
        EditVersion.requireUnchanged(form.version(), draft.getLockVersion());

        CompanyContext company = current.require();
        draft.commit(clean(form.note()), company.userId());
        FloorPlanVersionEntity committed = versions.saveAndFlush(draft);
        level.useFloorPlan(committed.getId());
        levels.saveAndFlush(level);
        return summary(committed, committed.getId());
    }

    /** The house's floor-plan history, newest first. */
    @Transactional(readOnly = true)
    public List<FloorPlanVersion> history(UUID houseId) {
        LevelEntity level = groundFloor(houses.require(houseId));
        return versions.findByLevelIdOrderByVersionNoDesc(level.getId()).stream()
                .map(v -> summary(v, level.getCurrentFloorPlanVersionId()))
                .toList();
    }

    /** One version, to look at. */
    @Transactional(readOnly = true)
    public FloorPlanDocument version(UUID houseId, int versionNo) {
        LevelEntity level = groundFloor(houses.require(houseId));
        return document(houseId, findVersion(level, versionNo));
    }

    /** An earlier version's contents become the draft (replacing the draft there is, which must be the one the caller saw). */
    public FloorPlanDocument restore(UUID houseId, int versionNo, RestoreForm form) {
        HouseEntity house = editableHouse(houseId);
        LevelEntity level = groundFloor(house);
        FloorPlanVersionEntity earlier = findVersion(level, versionNo);
        if (earlier.isDraft()) {
            throw ProjectsProblem.conflict("Version " + versionNo + " is the draft already.");
        }

        FloorPlanVersionEntity draft = versions.findDraft(level.getId()).orElse(null);
        if (draft == null) {
            draft = newDraft(level, FloorPlanOrigin.EDITOR, earlier.getId());
        } else {
            EditVersion.requireUnchanged(form.version(), draft.getLockVersion());
            draft.basedOn(earlier.getId());
        }
        FloorPlan plan = FloorPlanReading.read(FloorPlanReading.tree(earlier.getDocument()));
        draft.replaceDocument(earlier.getDocument(), FloorPlanReading.figures(plan, earlier.getDocument()));
        return document(houseId, versions.saveAndFlush(draft));
    }

    // ---- helpers ----

    private FloorPlanVersionEntity newDraft(LevelEntity level, FloorPlanOrigin origin, @Nullable UUID basedOn) {
        CompanyContext company = current.require();
        return FloorPlanVersionEntity.draft(company.organisationId(), level.getId(), versions.nextVersionNo(level.getId()),
                origin, basedOn, company.userId());
    }

    /** The house, if it and its project may be changed (neither archived). */
    private HouseEntity editableHouse(UUID houseId) {
        HouseEntity house = houses.require(houseId);
        ProjectEntity project = projects.require(house.getProjectId());
        if (house.archived() || project.archived()) {
            throw ProjectsProblem.conflict("This house is archived, or its project is. Restore it to change its floor plan.");
        }
        return house;
    }

    /** v1 houses have one storey: the ground floor, made with the house. */
    private LevelEntity groundFloor(HouseEntity house) {
        return levels.findByHouseIdOrderByOrdinalAsc(house.getId()).stream()
                .filter(l -> l.getOrdinal() == 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("House " + house.getId() + " has no ground floor"));
    }

    private FloorPlanVersionEntity findVersion(LevelEntity level, int versionNo) {
        return versions.findByLevelIdAndVersionNo(level.getId(), versionNo)
                .orElseThrow(() -> ProjectsProblem.missing("This house has no floor-plan version " + versionNo + "."));
    }

    private @Nullable UUID latestCommittedId(LevelEntity level) {
        return versions.findFirstByLevelIdAndStateOrderByVersionNoDesc(level.getId(), FloorPlanState.COMMITTED)
                .map(FloorPlanVersionEntity::getId).orElse(null);
    }

    private FloorPlanDocument document(UUID houseId, FloorPlanVersionEntity v) {
        Integer basedOn = v.getBasedOnVersionId() == null ? null
                : versions.findById(v.getBasedOnVersionId()).map(FloorPlanVersionEntity::getVersionNo).orElse(null);
        return new FloorPlanDocument(houseId, v.getLevelId(), v.getVersionNo(), v.getState(), v.getLockVersion(), basedOn,
                FloorPlanReading.tree(v.getDocument()), v.getUpdatedAt(), v.isDraft() ? v.getCreatedBy() : v.getCommittedBy());
    }

    private static FloorPlanVersion summary(FloorPlanVersionEntity v, @Nullable UUID currentId) {
        return new FloorPlanVersion(v.getVersionNo(), v.getState(), v.getId().equals(currentId), v.getNote(),
                v.getRoomCount(), v.getWallCount(), v.getOpeningCount(), v.getFloorAreaM2(), v.getOpenCheckCount(),
                v.getUpdatedAt(), v.isDraft() ? v.getCreatedBy() : v.getCommittedBy(), v.getCommittedAt());
    }

    private static @Nullable String clean(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
