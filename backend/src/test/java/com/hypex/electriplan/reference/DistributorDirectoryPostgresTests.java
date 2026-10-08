package com.hypex.electriplan.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hypex.electriplan.PostgresApplicationTest;
import com.hypex.electriplan.model.Samples;
import com.hypex.electriplan.model.brief.DistributorCode;
import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.users.SupabaseUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The reference module against a real Postgres migrated by Flyway: Hibernate
 * validates every entity against the actual tables when the context starts
 * (spring.jpa.hibernate.ddl-auto=validate), and the seeded distributors come
 * back through the repository, the directory and the endpoint.
 */
class DistributorDirectoryPostgresTests extends PostgresApplicationTest {

    @Autowired DistributorDirectory directory;
    @Autowired MockMvc mvc;
    @MockitoBean SupabaseUsers users;

    @Test
    void readsTheSeededVictorianDistributorsInOrder() {
        assertThat(directory.inState(AustralianState.VIC)).extracting(d -> d.name())
                .containsExactly("AusNet Services", "CitiPower", "Jemena", "Powercor", "United Energy");
        assertThat(directory.all()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(directory.inState(AustralianState.TAS)).isEmpty();
    }

    @Test
    void findsOneByCode() {
        assertThat(directory.find(DistributorCode.of("united_energy")))
                .hasValueSatisfying(d -> assertThat(d.name()).isEqualTo("United Energy"));
        assertThat(directory.find(DistributorCode.of("nobody"))).isEmpty();
    }

    @Test
    void checksBriefsAgainstTheTable() {
        assertThat(directory.check(Samples.brief())).isEmpty();
        assertThat(directory.check(Samples.brief().withDistributor(DistributorCode.of("ausgrid"))))
                .singleElement()
                .satisfies(p -> assertThat(p.message()).contains("'ausgrid'").contains("citipower"));
    }

    @Test
    void servesThemThroughTheEndpoint() throws Exception {
        mvc.perform(get("/api/reference/distributors").param("state", "VIC")
                        .with(jwt().jwt(j -> j.subject("44444444-4444-4444-4444-444444444444"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].code").value("ausnet_services"))
                .andExpect(jsonPath("$[0].state").value("VIC"));
    }
}
