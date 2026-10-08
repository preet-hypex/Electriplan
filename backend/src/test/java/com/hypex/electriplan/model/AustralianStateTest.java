package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.hypex.electriplan.model.common.AustralianState;
import org.junit.jupiter.api.Test;

class AustralianStateTest {

    @Test
    void eachStateHasItsFullNameAndIsFoundByIt() {
        assertThat(AustralianState.VIC.fullName()).isEqualTo("Victoria");
        assertThat(AustralianState.byFullName(" new south wales ")).contains(AustralianState.NSW);
        assertThat(AustralianState.byFullName("Australian Capital Territory")).contains(AustralianState.ACT);
        assertThat(AustralianState.byFullName("Christmas Island")).isEmpty();
        for (AustralianState state : AustralianState.values()) {
            assertThat(AustralianState.byFullName(state.fullName())).contains(state);
        }
    }
}
