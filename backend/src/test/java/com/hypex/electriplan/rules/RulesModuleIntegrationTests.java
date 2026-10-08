package com.hypex.electriplan.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.users.SupabaseUsers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** In the running application: the pack loads at startup, and production settings design no unsigned state. */
class RulesModuleIntegrationTests {

    @Nested
    @SpringBootTest
    class ProductionSettings {

        @Autowired RuleBook book;
        @MockitoBean SupabaseUsers users;

        @Test
        void theShippedPackIsLoadedAndNoUnsignedStateIsDesigned() {
            assertThat(book.pack().id()).isEqualTo("au-residential");
            assertThat(book.allowsUnsignedStates()).isFalse();
            assertThat(book.designableStates()).isEqualTo(book.pack().supportedStates());
            assertThat(book.designs(AustralianState.VIC)).isEqualTo(book.pack().supportedStates().contains(AustralianState.VIC));
        }
    }

    @Nested
    @SpringBootTest(properties = "electriplan.rules.allow-unsigned-states=true")
    class DevelopmentSettings {

        @Autowired RuleBook book;
        @MockitoBean SupabaseUsers users;

        @Test
        void statesWithAFileAreDesigned() {
            assertThat(book.allowsUnsignedStates()).isTrue();
            assertThat(book.designableStates()).isEqualTo(book.pack().states());
        }
    }
}
