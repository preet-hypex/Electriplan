package com.hypex.electriplan.tenancy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.hypex.electriplan.security.AuthenticatedUsers;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's companies (for the company switcher), and the company a request acts in. */
@RestController
@RequestMapping("/api/organisations")
@RequiredArgsConstructor
class OrganisationController {

    private final MembershipRepository memberships;
    private final AuthenticatedUsers callers;
    private final CurrentCompany current;
    private final OrganisationRepository organisations;

    record Membership(UUID id, String name, MemberRole role, LicenceStatus licence) {
    }

    record Current(UUID id, String name, String slug, MemberRole role, LicenceStatus licence,
                   int seatLimit, LocalDate licenceStartsOn, @Nullable LocalDate licenceEndsOn) {
    }

    /** Every company the caller can work in: active memberships of companies that are not closed. */
    @GetMapping
    @Transactional(readOnly = true)
    List<Membership> mine() {
        return memberships.usableBy(callers.require().id()).stream()
                .map(m -> new Membership(m.getId().getOrganisationId(), m.getOrganisation().getName(),
                        m.getRole(), m.getOrganisation().getStatus()))
                .toList();
    }

    /** The company this request acts in, read under row-level security. */
    @GetMapping("/current")
    @CompanyScoped
    @Transactional(readOnly = true)
    Current current() {
        CompanyContext company = current.require();
        OrganisationEntity o = organisations.findById(company.organisationId())
                .orElseThrow(() -> new IllegalStateException("The current company is not visible under row-level security"));
        return new Current(o.getId(), o.getName(), o.getSlug(), company.role(), o.getStatus(),
                o.getSeatLimit(), o.getLicenceStartsOn(), o.getLicenceEndsOn());
    }
}
