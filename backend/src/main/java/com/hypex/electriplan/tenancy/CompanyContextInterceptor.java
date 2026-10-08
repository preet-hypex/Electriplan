package com.hypex.electriplan.tenancy;

import com.hypex.electriplan.security.AuthenticatedUsers;

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
class CompanyContextInterceptor implements HandlerInterceptor {

    private final AuthenticatedUsers callers;
    private final CompanyResolver resolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        TenantSession.clear();
        callers.current().ifPresent(caller -> TenantSession.actor(caller.id()));

        if (handler instanceof HandlerMethod method && isCompanyScoped(method)) {
            var caller = callers.require();
            try {
                TenantSession.company(resolver.resolve(caller.id(), request.getHeader(CompanyResolver.HEADER)));
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

    private static boolean isCompanyScoped(HandlerMethod method) {
        return method.hasMethodAnnotation(CompanyScoped.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), CompanyScoped.class);
    }
}
