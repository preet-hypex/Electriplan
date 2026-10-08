package com.hypex.electriplan.apidocs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hypex.electriplan.users.SupabaseUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * contracts/openapi.json is exactly what the API describes. The web app's
 * TypeScript types come from that file, so a controller change without
 * regenerating it fails here. To regenerate: run this test with
 * {@code -Dopenapi.write=true} (see contracts/README.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    static final Path CONTRACT = Path.of("../contracts/openapi.json");

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Autowired MockMvc mvc;
    @MockitoBean SupabaseUsers users;

    @Test
    void theCommittedContractMatchesTheApi() throws Exception {
        String live = canonical(mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        if (Boolean.getBoolean("openapi.write")) {
            Files.writeString(CONTRACT, live);
            return;
        }
        assertThat(Files.exists(CONTRACT)).as("%s is missing", CONTRACT).isTrue();
        assertThat(Files.readString(CONTRACT))
                .as("contracts/openapi.json is out of date with the API. Regenerate it with "
                        + "`mvn test -Dtest=OpenApiContractTest -Dopenapi.write=true`, then `npm run contracts` in frontend/.")
                .isEqualTo(live);
    }

    /** Sorted keys, two-space indent, trailing newline: stable diffs whatever springdoc's order. */
    static String canonical(String json) throws Exception {
        Object tree = JSON.readValue(json, Object.class);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(Separators.createDefaultInstance()
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                .withObjectEmptySeparator("")
                .withArrayEmptySeparator(""))
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        return JSON.writer(printer).writeValueAsString(tree) + "\n";
    }
}
