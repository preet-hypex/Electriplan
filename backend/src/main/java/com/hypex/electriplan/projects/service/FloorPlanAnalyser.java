package com.hypex.electriplan.projects.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypex.electriplan.projects.config.AnalyserConfiguration;
import com.hypex.electriplan.projects.domain.AnalysisFailedException;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Asks the floor-plan analyser (the Python service) to read an image into a
 * FloorPlan. It is given the image's bytes and the URL the plan should point
 * at for it (where the API serves the stored image); it stores nothing.
 */
@Service
public class FloorPlanAnalyser {

    private final RestClient analyser;

    public FloorPlanAnalyser(@Qualifier(AnalyserConfiguration.CLIENT) RestClient analyser) {
        this.analyser = analyser;
    }

    /**
     * The FloorPlan the analyser reads from the image.
     *
     * @param accessToken the caller's Supabase token: the analyser accepts signed-in people only
     * @throws AnalysisFailedException with the analyser's own reason, or that it is not answering
     */
    public JsonNode analyse(byte[] image, String fileName, String contentType, String imageUrl,
                            @Nullable Double mmPerPx, String accessToken) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        form.add("image_url", imageUrl);
        if (mmPerPx != null) {
            form.add("mm_per_px", mmPerPx.toString());
        }
        try {
            JsonNode plan = analyser.post()
                    .uri("/api/floorplan/analyse")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (plan == null) {
                throw new AnalysisFailedException("The analyser gave no plan.");
            }
            return plan;
        } catch (RestClientResponseException e) {
            throw new AnalysisFailedException(reason(e), false, e);
        } catch (ResourceAccessException e) {
            throw new AnalysisFailedException("The floor-plan analyser is not answering. Try again in a moment, or trace the walls by hand.", true, e);
        } catch (RestClientException e) {
            throw new AnalysisFailedException("The analyser's answer could not be read: " + e.getMessage(), false, e);
        }
    }

    /** The analyser's own words ({"detail": "..."}), or its status. */
    private static String reason(RestClientResponseException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            if (body != null && body.path("detail").isTextual()) {
                return body.path("detail").asText();
            }
        } catch (RuntimeException ignored) {
            // not JSON: fall through to the status
        }
        return "The analyser could not read the image (HTTP " + e.getStatusCode().value() + ").";
    }
}
