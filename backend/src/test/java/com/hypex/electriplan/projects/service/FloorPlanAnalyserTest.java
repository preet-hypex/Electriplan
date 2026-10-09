package com.hypex.electriplan.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import com.hypex.electriplan.projects.domain.AnalysisFailedException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The analyser client's request and how it reads the answers, against a pretend analyser. */
class FloorPlanAnalyserTest {

    MockRestServiceServer server;
    FloorPlanAnalyser analyser;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://analyser.test");
        server = MockRestServiceServer.bindTo(builder).build();
        analyser = new FloorPlanAnalyser(builder.build());
    }

    @Test
    void sendsTheImageAndWhereThePlanShouldPointWithTheCallersToken() {
        server.expect(requestTo("http://analyser.test/api/floorplan/analyse"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(Matchers.containsString("name=\"image_url\"")))
                .andExpect(content().string(Matchers.containsString("/api/files/f1")))
                .andExpect(content().string(Matchers.containsString("filename=\"plan.png\"")))
                .andExpect(content().string(Matchers.containsString("name=\"mm_per_px\"")))
                .andRespond(withSuccess("{\"version\": 1, \"units\": \"mm\"}", MediaType.APPLICATION_JSON));

        assertThat(analyser.analyse(new byte[] {1, 2}, "plan.png", "image/png", "/api/files/f1", 12.5, "tok")
                .path("units").asText()).isEqualTo("mm");
        server.verify();
    }

    @Test
    void passesOnTheAnalysersOwnReason() {
        server.expect(requestTo("http://analyser.test/api/floorplan/analyse"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\": \"No walls were found in the image.\"}"));
        assertThatThrownBy(() -> analyser.analyse(new byte[] {1}, "p.png", "image/png", "/api/files/f1", null, "tok"))
                .isInstanceOf(AnalysisFailedException.class)
                .hasMessage("No walls were found in the image.")
                .matches(e -> !((AnalysisFailedException) e).unavailable());
    }

    @Test
    void saysWhenItIsNotAnswering() {
        ClientHttpRequestFactory refusing = (uri, method) -> { throw new IOException("Connection refused"); };
        FloorPlanAnalyser down = new FloorPlanAnalyser(RestClient.builder().baseUrl("http://analyser.test").requestFactory(refusing).build());
        assertThatThrownBy(() -> down.analyse(new byte[] {1}, "p.png", "image/png", "/api/files/f1", null, "tok"))
                .isInstanceOf(AnalysisFailedException.class)
                .hasMessageContaining("not answering")
                .matches(e -> ((AnalysisFailedException) e).unavailable());
    }
}
