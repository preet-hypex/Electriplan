package com.hypex.electriplan.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Every rule for choosing a request's company, with the database replaced. */
class CompanyResolverTest {

    private static final UUID ME = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final OrganisationEntity ACME = org("a0000000-0000-4000-8000-000000000001", "Acme", LicenceStatus.ACTIVE);
    private static final OrganisationEntity BOLT = org("b0000000-0000-4000-8000-000000000002", "Bolt", LicenceStatus.SUSPENDED);

    private final MembershipRepository repository = mock(MembershipRepository.class);
    private final CompanyResolver resolver = new CompanyResolver(repository);

    private static OrganisationEntity org(String id, String name, LicenceStatus status) {
        return new OrganisationEntity(UUID.fromString(id), name, name.toLowerCase(), status, 3, LocalDate.now(), null, null);
    }

    private static MemberEntity member(OrganisationEntity org, MemberRole role) {
        return new MemberEntity(new MemberId(org.getId(), ME), org, role, MemberStatus.ACTIVE, Instant.now());
    }

    private void memberOf(MemberEntity... memberships) {
        given(repository.usableBy(ME)).willReturn(List.of(memberships));
    }

    private void refused(String header, HttpStatus status, String message) {
        assertThatThrownBy(() -> resolver.resolve(ME, header))
                .isInstanceOfSatisfying(CompanyAccessException.class, e -> {
                    assertThat(e.status()).isEqualTo(status);
                    assertThat(e.getMessage()).contains(message);
                });
    }

    @Test
    void theHeaderChoosesAmongTheCallersCompanies() {
        memberOf(member(ACME, MemberRole.OWNER), member(BOLT, MemberRole.VIEWER));
        assertThat(resolver.resolve(ME, BOLT.getId().toString()))
                .isEqualTo(new CompanyContext(BOLT.getId(), ME, MemberRole.VIEWER, LicenceStatus.SUSPENDED));
        assertThat(resolver.resolve(ME, " " + ACME.getId() + " ").role()).isEqualTo(MemberRole.OWNER);
    }

    @Test
    void aCallerWithOneCompanyNeedNotSayWhich() {
        memberOf(member(ACME, MemberRole.BUILDER));
        assertThat(resolver.resolve(ME, null)).isEqualTo(new CompanyContext(ACME.getId(), ME, MemberRole.BUILDER, LicenceStatus.ACTIVE));
        assertThat(resolver.resolve(ME, "  ").organisationId()).isEqualTo(ACME.getId());
    }

    @Test
    void aCallerWithSeveralCompaniesMustSayWhich() {
        memberOf(member(ACME, MemberRole.OWNER), member(BOLT, MemberRole.VIEWER));
        refused(null, HttpStatus.BAD_REQUEST, "You belong to 2 companies: choose one with the X-Organisation-Id header.");
    }

    @Test
    void aCallerWithNoCompanyIsRefused() {
        memberOf();
        refused(null, HttpStatus.FORBIDDEN, "You are not a member of any company yet");
    }

    @Test
    void anotherCompanyAndAnUnknownOneGetTheSameRefusal() {
        memberOf(member(ACME, MemberRole.OWNER));
        refused(BOLT.getId().toString(), HttpStatus.FORBIDDEN, "You are not a member of that company, or it is closed.");
        refused(UUID.randomUUID().toString(), HttpStatus.FORBIDDEN, "You are not a member of that company, or it is closed.");
    }

    @Test
    void aHeaderThatIsNotAnIdIsABadRequest() {
        memberOf(member(ACME, MemberRole.OWNER));
        refused("acme", HttpStatus.BAD_REQUEST, "X-Organisation-Id must be a company id");
        refused("a0000000-0000", HttpStatus.BAD_REQUEST, "X-Organisation-Id must be a company id");
    }
}
