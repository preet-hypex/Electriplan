package com.hypex.electriplan.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hypex.electriplan.model.brief.DistributorCode;
import com.hypex.electriplan.model.brief.ProjectBrief;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Distributor codes are configurable data: any well-formed code is accepted here; existence is the reference module's check. */
class DistributorCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"jemena", "united_energy", "ausnet_services", "ausgrid", "essential_energy", "x", "dnsp2"})
    void acceptsAnyWellFormedCode(String code) {
        assertThat(DistributorCode.of(code).value()).isEqualTo(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Jemena", "united-energy", "origin energy", "jemena!", "ü"})
    void refusesBadlyFormedCodes(String code) {
        assertThatThrownBy(() -> DistributorCode.of(code)).hasMessageContaining("distributor");
    }

    @Test
    void refusesCodesLongerThanTheDatabaseColumnAllows() {
        assertThat(DistributorCode.of("a".repeat(40)).value()).hasSize(40);
        assertThatThrownBy(() -> DistributorCode.of("a".repeat(41))).hasMessageContaining("distributor");
    }

    @Test
    void isAPlainStringInJson() {
        assertThat(ModelJson.write(DistributorCode.of("jemena"))).isEqualTo("\"jemena\"");
        assertThat(ModelJson.read("\"powercor\"", DistributorCode.class)).isEqualTo(DistributorCode.of("powercor"));
        assertThatThrownBy(() -> ModelJson.read("42", DistributorCode.class)).isInstanceOf(ModelJson.ModelJsonException.class);
    }

    @Test
    void aBriefMayNameADistributorTheCodeDoesNotKnowYet() {
        ProjectBrief brief = Samples.brief().withDistributor(DistributorCode.of("ausgrid"));
        assertThat(ModelJson.read(ModelJson.write(brief), ProjectBrief.class).distributor().value()).isEqualTo("ausgrid");
    }

    @Test
    void printsAsItsCode() {
        assertThat(DistributorCode.of("citipower")).hasToString("citipower");
    }
}
