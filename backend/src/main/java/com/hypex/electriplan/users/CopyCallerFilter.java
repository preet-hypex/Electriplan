package com.hypex.electriplan.users;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.hypex.electriplan.security.AuthenticatedUsers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class CopyCallerFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CopyCallerFilter.class);

    private final AuthenticatedUsers callers;
    private final SupabaseUsers users;

    CopyCallerFilter(AuthenticatedUsers callers, SupabaseUsers users) {
        this.callers = callers;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        callers.current().ifPresent(caller -> {
            try {
                users.copyIfMissing(caller.id());
            } catch (RuntimeException e) {
                log.warn("Could not copy {} from Supabase yet; the scheduled sync will retry: {}", caller.id(), e.getMessage());
            }
        });
        chain.doFilter(request, response);
    }
}
