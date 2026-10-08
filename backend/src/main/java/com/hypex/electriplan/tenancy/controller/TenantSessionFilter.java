package com.hypex.electriplan.tenancy.controller;

import java.io.IOException;

import com.hypex.electriplan.tenancy.service.TenantSession;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guarantees the request's tenant state never outlives the request. The
 * interceptor clears it too, but Spring skips an interceptor's clean-up when
 * the interceptor itself refuses the request; a filter's finally always runs.
 */
@Component
public class TenantSessionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            TenantSession.clear();
        }
    }
}
