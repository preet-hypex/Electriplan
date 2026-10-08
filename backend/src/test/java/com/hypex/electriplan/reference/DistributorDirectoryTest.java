package com.hypex.electriplan.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import com.hypex.electriplan.model.Samples;
import com.hypex.electriplan.model.brief.DistributorCode;
import com.hypex.electriplan.model.brief.ProjectBrief;
import com.hypex.electriplan.model.common.AustralianState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The directory's logic, with the repository replaced: every outcome of checking a brief's distributor. */
class DistributorDirectoryTest {

    private static final DistributorEntity JEMENA = new DistributorEntity("jemena", "Jemena", AustralianState.VIC);
    private static final DistributorEntity POWERCOR = new DistributorEntity("powercor", "Powercor", AustralianState.VIC);
    private static final DistributorEntity AUSGRID = new DistributorEntity("ausgrid", "Ausgrid", AustralianState.NSW);

    private final DistributorRepository repository = mock(DistributorRepository.class);
    private final DistributorDirectory directory = new DistributorDirectory(repository);

    @BeforeEach
    void knownDistributors() {
        given(repository.findById("jemena")).willReturn(Optional.of(JEMENA));
        given(repository.findById("ausgrid")).willReturn(Optional.of(AUSGRID));
        given(repository.findByStateOrderByNameAsc(AustralianState.VIC)).willReturn(List.of(JEMENA, POWERCOR));
        given(repository.findByStateOrderByNameAsc(AustralianState.NSW)).willReturn(List.of(AUSGRID));
        given(repository.findAllByOrderByStateAscNameAsc()).willReturn(List.of(AUSGRID, JEMENA, POWERCOR));
    }

    private static ProjectBrief brief(AustralianState state, String distributor) {
        return Samples.brief().withState(state).withDistributor(DistributorCode.of(distributor));
    }

    @Test
    void aKnownDistributorInTheBriefsStateIsFine() {
        assertThat(directory.check(brief(AustralianState.VIC, "jemena"))).isEmpty();
        assertThat(directory.check(brief(AustralianState.NSW, "ausgrid"))).isEmpty();
    }

    @Test
    void anUnknownCodeIsAProblemThatListsTheChoices() {
        assertThat(directory.check(brief(AustralianState.VIC, "origin")))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.field()).isEqualTo("distributor");
                    assertThat(p.message()).contains("No distributor has the code 'origin'").contains("jemena, powercor");
                });
    }

    @Test
    void aDistributorFromAnotherStateIsAProblem() {
        assertThat(directory.check(brief(AustralianState.VIC, "ausgrid")))
                .singleElement()
                .satisfies(p -> assertThat(p.message()).contains("Ausgrid supplies NSW, not VIC").contains("jemena, powercor"));
    }

    @Test
    void aStateWithNoDistributorsSetUpSaysSo() {
        given(repository.findByStateOrderByNameAsc(AustralianState.TAS)).willReturn(List.of());
        assertThat(directory.check(brief(AustralianState.TAS, "tasnetworks")))
                .singleElement()
                .satisfies(p -> assertThat(p.message()).contains("none yet: no distributor in TAS is set up"));
    }

    @Test
    void listsAndFindsDistributorsAsModelValues() {
        assertThat(directory.all()).extracting(d -> d.code().value()).containsExactly("ausgrid", "jemena", "powercor");
        assertThat(directory.inState(AustralianState.VIC)).containsExactly(
                new Distributor(DistributorCode.of("jemena"), "Jemena", AustralianState.VIC),
                new Distributor(DistributorCode.of("powercor"), "Powercor", AustralianState.VIC));
        assertThat(directory.find(DistributorCode.of("jemena"))).contains(
                new Distributor(DistributorCode.of("jemena"), "Jemena", AustralianState.VIC));
        assertThat(directory.find(DistributorCode.of("nobody"))).isEmpty();
    }
}
