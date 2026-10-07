package com.hypex.planna;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.hypex.planna.users.SupabaseUser;
import com.hypex.planna.users.SupabaseUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class ApiAccessTests {

    private static final UUID SUB = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired MockMvc mvc;
    @MockitoBean SupabaseUsers users;

    private static RequestPostProcessor anySupabaseUser() {
        return jwt().jwt(j -> j.subject(SUB.toString()).claim("email", "sam@example.com"));
    }

    @Test
    void noTokenIsUnauthorised() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test
    void anyoneSupabaseSignedInGetsThroughWithNoRoleOrInvitationCheck() throws Exception {
        Instant now = Instant.now();
        given(users.find(SUB)).willReturn(Optional.of(new SupabaseUser(SUB, "sam@example.com", null,
                Map.of("full_name", "Sam Lee"), Map.of("provider", "email"), now, null, now, null, now, now, now)));

        mvc.perform(get("/api/me").with(anySupabaseUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SUB.toString()))
                .andExpect(jsonPath("$.email").value("sam@example.com"))
                .andExpect(jsonPath("$.copy.userMetadata.full_name").value("Sam Lee"));
    }

    @Test
    void theCallerIsCopiedFromSupabaseIfTheCopyDoesNotHaveThemYet() throws Exception {
        mvc.perform(get("/api/me").with(anySupabaseUser())).andExpect(status().isOk());
        verify(users).copyIfMissing(SUB);
    }

    @Test
    void aFailedCopyDoesNotStopTheRequest() throws Exception {
        willThrow(new IllegalStateException("Supabase is down")).given(users).copyIfMissing(SUB);

        mvc.perform(get("/api/me").with(anySupabaseUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copy").doesNotExist());
    }

    @Test
    void healthIsPublicAndEverythingOutsideTheApiIsDenied() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/somewhere-else").with(anySupabaseUser())).andExpect(status().isForbidden());
        verify(users, never()).copyIfMissing(SUB);
    }
}
