package com.hypex.electriplan.projects.service;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypex.electriplan.files.domain.FilePurpose;
import com.hypex.electriplan.files.domain.UnsupportedFileException;
import com.hypex.electriplan.files.dto.StoredFile;
import com.hypex.electriplan.files.service.CompanyFiles;
import com.hypex.electriplan.model.ModelJson;
import com.hypex.electriplan.projects.dao.AnalysisRunRepository;
import com.hypex.electriplan.projects.domain.AnalysisFailedException;
import com.hypex.electriplan.projects.domain.ProjectsProblem;
import com.hypex.electriplan.projects.dto.FloorPlanDocument;
import com.hypex.electriplan.projects.entity.AnalysisRunEntity;
import com.hypex.electriplan.tenancy.domain.CompanyContext;
import com.hypex.electriplan.tenancy.service.CurrentCompany;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * A floor-plan image uploaded for a house (P5), in three steps:
 *
 * <ol>
 *   <li>Keep the image in the company's files (S3) and record an analysis run, committed; the house is analysing.</li>
 *   <li>Ask the analyser to read it, outside any transaction (it can take a minute).</li>
 *   <li>Record how the run went; on success its plan becomes the house's draft.</li>
 * </ol>
 *
 * A failed analysis is recorded with its reason, and the image stays kept.
 */
@Service
@RequiredArgsConstructor
public class HouseUploadsService {

    private final CompanyFiles files;
    private final FloorPlansService floorPlans;
    private final FloorPlanAnalyser analyser;
    private final AnalysisRunRepository runs;
    private final CurrentCompany current;
    private final TransactionTemplate transaction;

    /** What was kept and started in step 1. */
    private record Started(UUID runId, StoredFile file, byte[] bytes) {
    }

    /**
     * Analyses the uploaded image into the house's draft.
     *
     * @param mmPerPx a known scale, instead of the analyser estimating it
     * @param version the draft's version, when the house has a draft: the analysed plan replaces it
     */
    public FloorPlanDocument upload(UUID houseId, MultipartFile upload, @Nullable Double mmPerPx, @Nullable Integer version) {
        if (mmPerPx != null && mmPerPx <= 0) {
            throw ProjectsProblem.invalid("mmPerPx", "A scale is greater than zero");
        }
        Started started = transaction.execute(tx -> keepAndStart(houseId, upload, mmPerPx));

        JsonNode plan;
        try {
            plan = analyser.analyse(started.bytes(), fileName(upload), started.file().contentType(), started.file().url(),
                    mmPerPx, accessToken());
        } catch (AnalysisFailedException e) {
            transaction.executeWithoutResult(tx -> {
                runs.findById(started.runId()).ifPresent(run -> run.failed(e.getMessage()));
                floorPlans.analysisFailed(houseId);
            });
            throw e.unavailable() ? ProjectsProblem.unavailable(e.getMessage()) : ProjectsProblem.unprocessable(e.getMessage());
        }

        return transaction.execute(tx -> {
            runs.findById(started.runId()).ifPresent(run -> run.succeeded(
                    ModelJson.write(plan.path("analysis").path("steps")),
                    ModelJson.write(plan.path("analysis").path("warnings"))));
            return floorPlans.saveAnalysed(houseId, plan, version, started.runId(), started.file().id());
        });
    }

    private Started keepAndStart(UUID houseId, MultipartFile upload, @Nullable Double mmPerPx) {
        UUID levelId = floorPlans.startAnalysis(houseId);
        byte[] bytes = read(upload);
        StoredFile file = files.storeImage(bytes, upload.getOriginalFilename(), FilePurpose.FLOOR_PLAN_SOURCE);
        CompanyContext company = current.require();
        String parameters = ModelJson.write(mmPerPx == null ? Map.of() : Map.of("mmPerPx", mmPerPx));
        AnalysisRunEntity run = runs.saveAndFlush(
                AnalysisRunEntity.starting(company.organisationId(), levelId, file.id(), parameters, company.userId()));
        return new Started(run.getId(), file, bytes);
    }

    private static byte[] read(MultipartFile upload) {
        try {
            return upload.getBytes();
        } catch (IOException e) {
            throw new UnsupportedFileException("The upload could not be read: " + e.getMessage());
        }
    }

    private static String fileName(MultipartFile upload) {
        String name = upload.getOriginalFilename();
        return name == null || name.isBlank() ? "floor-plan" : name;
    }

    /** The caller's own token, passed on: the analyser serves signed-in people only. */
    private static String accessToken() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        throw new IllegalStateException("An upload is always made by a signed-in person");
    }
}
