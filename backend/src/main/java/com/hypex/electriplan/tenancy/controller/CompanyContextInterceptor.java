package com.hypex.electriplan.tenancy.controller;

import com.hypex.electriplan.security.AuthenticatedUsers;
import com.hypex.electriplan.tenancy.domain.CompanyAccessException;
import com.hypex.electriplan.tenancy.domain.CompanyContext;
import com.hypex.electriplan.tenancy.domain.CompanyScoped;
import com.hypex.electriplan.tenancy.domain.MemberRole;
import com.hypex.electriplan.tenancy.domain.Permission;
import com.hypex.electriplan.tenancy.domain.PermissionMatrix;
import com.hypex.electriplan.tenancy.domain.RequiresPermission;
import com.hypex.electriplan.tenancy.service.CompanyResolver;
import com.hypex.electriplan.tenancy.service.TenantSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * For every API request: records who is acting. For a {@link CompanyScoped}
 * endpoint: resolves and checks the company before the endpoint runs. Always
 * clears both when the request ends, so nothing leaks to the thread's next
 * request.
 */
@Component
@RequiredArgsConstructor
public class CompanyContextInterceptor implements HandlerInterceptor {

    private final AuthenticatedUsers callers;
    private final CompanyResolver resolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        TenantSession.clear();
        callers.current().ifPresent(caller -> TenantSession.actor(caller.id()));

        if (handler instanceof HandlerMethod method && isCompanyScoped(method)) {
            var caller = callers.require();
            try {
                CompanyContext company = resolver.resolve(caller.id(), request.getHeader(CompanyResolver.HEADER));
                Permission needed = requiredPermission(method);
                if (needed != null && !PermissionMatrix.allows(company.role(), needed)) {
                    throw refusal(company.role(), needed);
                }
                TenantSession.company(company);
            } catch (RuntimeException refused) {
                // Spring will not call afterCompletion for a refusing interceptor.
                TenantSession.clear();
                throw refused;
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        TenantSession.clear();
    }

    public static boolean isCompanyScoped(HandlerMethod method) {
        return method.hasMethodAnnotation(CompanyScoped.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), CompanyScoped.class)
                || requiredPermission(method) != null;
    }

    /** The method's @RequiresPermission, else the controller's, else none. */
    public static @Nullable Permission requiredPermission(HandlerMethod method) {
        RequiresPermission onMethod = method.getMethodAnnotation(RequiresPermission.class);
        if (onMethod != null) {
            return onMethod.value();
        }
        RequiresPermission onType = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresPermission.class);
        return onType == null ? null : onType.value();
    }

    public static CompanyAccessException refusal(MemberRole role, Permission needed) {
        String allowed = PermissionMatrix.rolesWith(needed).stream().map(MemberRole::code)
                .collect(java.util.stream.Collectors.joining(", "));
        return new CompanyAccessException(org.springframework.http.HttpStatus.FORBIDDEN,
                "As " + article(role) + " " + role.code() + " you cannot do this (" + needed.code() + "). Roles that can: "
                        + allowed + ".");
    }

    private static String article(MemberRole role) {
        return role == MemberRole.OWNER || role == MemberRole.ADMIN || role == MemberRole.ELECTRICIAN ? "an" : "a";
    }
}
