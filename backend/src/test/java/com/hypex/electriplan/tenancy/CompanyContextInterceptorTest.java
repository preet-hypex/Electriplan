package com.hypex.electriplan.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;

import com.hypex.electriplan.security.AuthenticatedUser;
import com.hypex.electriplan.security.AuthenticatedUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/** The interceptor records the actor, resolves the company only for scoped endpoints, and always clears up. */
class CompanyContextInterceptorTest {

    private static final UUID ME = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final CompanyContext ACME = new CompanyContext(
            UUID.fromString("a0000000-0000-4000-8000-000000000001"), ME, MemberRole.OWNER, LicenceStatus.ACTIVE);

    private final AuthenticatedUsers callers = mock(AuthenticatedUsers.class);
    private final CompanyResolver resolver = mock(CompanyResolver.class);
    private final CompanyContextInterceptor interceptor = new CompanyContextInterceptor(callers, resolver);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    static class Endpoints {
        @CompanyScoped public void scoped() { }
        public void open() { }
        @RequiresPermission(Permission.LICENCE_VIEW) public void licence() { }
    }

    @RequiresPermission(Permission.MEMBER_MANAGE)
    static class MembersController {
        public void list() { }
        @RequiresPermission(Permission.COMPANY_VIEW) public void view() { }
    }

    private CompanyContext as(MemberRole role) {
        return new CompanyContext(ACME.organisationId(), ME, role, LicenceStatus.ACTIVE);
    }

    @CompanyScoped
    static class ScopedController {
        public void anything() { }
    }

    private static HandlerMethod handler(Object bean, String name) throws Exception {
        return new HandlerMethod(bean, bean.getClass().getMethod(name));
    }

    private void signedIn() {
        AuthenticatedUser caller = new AuthenticatedUser(ME, "me@example.com", null);
        given(callers.current()).willReturn(Optional.of(caller));
        given(callers.require()).willReturn(caller);
    }

    @AfterEach
    void clear() {
        TenantSession.clear();
    }

    @Test
    void recordsTheActorOnEveryApiRequest() throws Exception {
        signedIn();
        interceptor.preHandle(request, response, handler(new Endpoints(), "open"));
        assertThat(TenantSession.actorId()).isEqualTo(ME);
        assertThat(TenantSession.company()).isNull();
        verify(resolver, never()).resolve(any(), any());
    }

    @Test
    void resolvesTheCompanyForAScopedEndpointFromTheHeader() throws Exception {
        signedIn();
        request.addHeader("X-Organisation-Id", ACME.organisationId().toString());
        given(resolver.resolve(ME, ACME.organisationId().toString())).willReturn(ACME);

        interceptor.preHandle(request, response, handler(new Endpoints(), "scoped"));
        assertThat(TenantSession.company()).isEqualTo(ACME);
        assertThat(new CurrentCompany().require()).isEqualTo(ACME);
    }

    @Test
    void aScopedControllerScopesEveryEndpoint() throws Exception {
        signedIn();
        given(resolver.resolve(ME, null)).willReturn(ACME);
        interceptor.preHandle(request, response, handler(new ScopedController(), "anything"));
        assertThat(TenantSession.company()).isEqualTo(ACME);
    }

    @Test
    void aRefusalStopsTheRequestBeforeTheEndpoint() throws Exception {
        signedIn();
        given(resolver.resolve(ME, null)).willThrow(new CompanyAccessException(org.springframework.http.HttpStatus.FORBIDDEN, "no"));
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler(new Endpoints(), "scoped")))
                .isInstanceOf(CompanyAccessException.class);
        assertThat(TenantSession.company()).isNull();
        assertThat(TenantSession.actorId()).as("a refusal leaves nothing on the thread").isNull();
    }

    @Test
    void clearsEverythingWhenTheRequestEnds() throws Exception {
        signedIn();
        given(resolver.resolve(ME, null)).willReturn(ACME);
        interceptor.preHandle(request, response, handler(new Endpoints(), "scoped"));
        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(TenantSession.actorId()).isNull();
        assertThat(TenantSession.company()).isNull();
        assertThat(new CurrentCompany().get()).isEmpty();
    }

    @Test
    void startsEveryRequestClean() throws Exception {
        TenantSession.company(ACME);          // left over from a request that never completed
        given(callers.current()).willReturn(Optional.empty());
        interceptor.preHandle(request, response, handler(new Endpoints(), "open"));
        assertThat(TenantSession.company()).isNull();
        assertThat(TenantSession.actorId()).isNull();
    }

    @Test
    void theFilterClearsUpWhateverHappened() throws Exception {
        TenantSession.company(ACME);
        jakarta.servlet.FilterChain failing = (req, res) -> { throw new IllegalStateException("endpoint blew up"); };
        assertThatThrownBy(() -> new TenantSessionFilter().doFilter(request, response, failing)).hasMessage("endpoint blew up");
        assertThat(TenantSession.company()).isNull();
        assertThat(TenantSession.actorId()).isNull();
    }

    @Test
    void aPermissionImpliesTheCompanyAndIsGrantedByTheRole() throws Exception {
        signedIn();
        given(resolver.resolve(ME, null)).willReturn(as(MemberRole.ADMIN));
        interceptor.preHandle(request, response, handler(new Endpoints(), "licence"));
        assertThat(TenantSession.company()).isEqualTo(as(MemberRole.ADMIN));
        assertThat(new CurrentCompany().can(Permission.LICENCE_VIEW)).isTrue();
        assertThat(new CurrentCompany().can(Permission.OWNERSHIP_TRANSFER)).isFalse();
    }

    @Test
    void aRoleWithoutThePermissionIsRefusedAndToldWhoCan() throws Exception {
        signedIn();
        given(resolver.resolve(ME, null)).willReturn(as(MemberRole.BUILDER));
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler(new Endpoints(), "licence")))
                .isInstanceOfSatisfying(CompanyAccessException.class, e -> {
                    assertThat(e.status()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).isEqualTo("As a builder you cannot do this (licence.view). Roles that can: owner, admin.");
                });
        assertThat(TenantSession.company()).isNull();
        assertThat(TenantSession.actorId()).isNull();
    }

    @Test
    void aMethodsPermissionOverridesItsControllers() throws Exception {
        signedIn();
        given(resolver.resolve(ME, null)).willReturn(as(MemberRole.VIEWER));
        interceptor.preHandle(request, response, handler(new MembersController(), "view"));
        assertThat(TenantSession.company()).isNotNull();
        TenantSession.clear();
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler(new MembersController(), "list")))
                .hasMessageContaining("member.manage");
    }

    @Test
    void servicesCanCheckAPermissionThemselves() {
        TenantSession.company(as(MemberRole.ELECTRICIAN));
        new CurrentCompany().require(Permission.DESIGN_SIGN_OFF);
        assertThatThrownBy(() -> new CurrentCompany().require(Permission.QUOTE_EDIT))
                .hasMessage("As an electrician you cannot do this (quote.edit). Roles that can: owner, admin, builder.");
    }

    @Test
    void currentCompanyOutsideAScopedEndpointIsAProgrammingError() {
        assertThatThrownBy(() -> new CurrentCompany().require()).hasMessageContaining("@CompanyScoped");
    }
}
