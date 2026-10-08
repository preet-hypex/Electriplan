package com.hypex.electriplan.tenancy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.hypex.electriplan.security.AuthenticatedUsers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's companies (for the company switcher), and the company a request acts in. */
@RestController
@Tag(name = "Companies", description = "The caller's companies, and the company a request acts in.")
@RequestMapping("/api/organisations")
@RequiredArgsConstructor
class OrganisationController {

    private final MembershipRepository memberships;
    private final AuthenticatedUsers callers;
    private final CurrentCompany current;
    private final OrganisationRepository organisations;
    private final Seats seats;

    record Membership(UUID id, String name, MemberRole role, LicenceStatus licence) {
    }

    record Current(UUID id, String name, String slug, MemberRole role, LicenceStatus licence,
                   Set<Permission> permissions) {
    }

    record Licence(LicenceStatus status, int seatLimit, long seatsInUse, LocalDate licenceStartsOn,
                   @Nullable LocalDate licenceEndsOn) {
    }

    /** Every company the caller can work in: active memberships of companies that are not closed. */
    @GetMapping
    @Operation(operationId = "listMyCompanies", summary = "The caller's companies",
            description = "Active memberships of companies that are not closed, by name: what the company switcher offers.")
    @Transactional(readOnly = true)
    List<Membership> mine() {
        return memberships.usableBy(callers.require().id()).stream()
                .map(m -> new Membership(m.getId().getOrganisationId(), m.getOrganisation().getName(),
                        m.getRole(), m.getOrganisation().getStatus()))
                .toList();
    }

    /**
     * The company this request acts in, the caller's role there, and what that
     * role may do, so the web app can show only the actions that will work.
     */
    @GetMapping("/current")
    @Operation(operationId = "getCurrentCompany", summary = "The company this request acts in",
            description = "With the caller's role there and the permissions that role has, so the app shows only actions that will work.")
    @CompanyScoped
    @Transactional(readOnly = true)
    Current current() {
        CompanyContext company = current.require();
        OrganisationEntity o = organisation(company);
        return new Current(o.getId(), o.getName(), o.getSlug(), company.role(), o.getStatus(),
                PermissionMatrix.permissions(company.role()));
    }

    /** The licence and how many of its seats are in use. Owners and admins only. */
    @GetMapping("/current/licence")
    @Operation(operationId = "getCurrentLicence", summary = "The company's licence and seats in use")
    @RequiresPermission(Permission.LICENCE_VIEW)
    @Transactional(readOnly = true)
    Licence licence() {
        OrganisationEntity o = organisation(current.require());
        return new Licence(o.getStatus(), o.getSeatLimit(), seats.inUse(o.getId()), o.getLicenceStartsOn(), o.getLicenceEndsOn());
    }

    private OrganisationEntity organisation(CompanyContext company) {
        return organisations.findById(company.organisationId())
                .orElseThrow(() -> new IllegalStateException("The current company is not visible under row-level security"));
    }
}
