package com.hypex.electriplan.apidocs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hypex.electriplan.users.service.SupabaseUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** API_DOCS_ENABLED=false turns off both the document and Swagger UI. */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
@AutoConfigureMockMvc
class ApiDocsDisabledTests {

    @Autowired MockMvc mvc;
    @MockitoBean SupabaseUsers users;

    @Test
    void neitherIsServed() throws Exception {
        mvc.perform(get("/api/docs/openapi.json")).andExpect(status().isNotFound());
        mvc.perform(get("/api/docs")).andExpect(status().isNotFound());
        mvc.perform(get("/api/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
