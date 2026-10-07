package com.hypex.electriplan.reference;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.hypex.electriplan.model.brief.DistributorCode;
import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.users.SupabaseUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The distributors endpoint through the real web and security stack, with the directory replaced. */
@SpringBootTest
@AutoConfigureMockMvc
class ReferenceControllerTests {

    @Autowired MockMvc mvc;
    @MockitoBean DistributorDirectory directory;
    @MockitoBean SupabaseUsers users;

    private static final Distributor JEMENA = new Distributor(DistributorCode.of("jemena"), "Jemena", AustralianState.VIC);
    private static final Distributor AUSGRID = new Distributor(DistributorCode.of("ausgrid"), "Ausgrid", AustralianState.NSW);

    private static RequestPostProcessor signedIn() {
        return jwt().jwt(j -> j.subject("44444444-4444-4444-4444-444444444444"));
    }

    @Test
    void needsASignIn() throws Exception {
        mvc.perform(get("/api/reference/distributors")).andExpect(status().isUnauthorized());
        verifyNoInteractions(directory);
    }

    @Test
    void listsEveryDistributorAsCodeNameAndState() throws Exception {
        given(directory.all()).willReturn(List.of(AUSGRID, JEMENA));
        mvc.perform(get("/api/reference/distributors").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("ausgrid"))
                .andExpect(jsonPath("$[0].name").value("Ausgrid"))
                .andExpect(jsonPath("$[0].state").value("NSW"))
                .andExpect(jsonPath("$[1].code").value("jemena"));
    }

    @Test
    void filtersByState() throws Exception {
        given(directory.inState(AustralianState.VIC)).willReturn(List.of(JEMENA));
        mvc.perform(get("/api/reference/distributors").param("state", "VIC").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("jemena"));
    }

    @Test
    void aStateWithNoneSetUpIsAnEmptyList() throws Exception {
        given(directory.inState(AustralianState.TAS)).willReturn(List.of());
        mvc.perform(get("/api/reference/distributors").param("state", "TAS").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anUnknownStateIsABadRequest() throws Exception {
        mvc.perform(get("/api/reference/distributors").param("state", "Victoria").with(signedIn()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(directory);
    }
}
