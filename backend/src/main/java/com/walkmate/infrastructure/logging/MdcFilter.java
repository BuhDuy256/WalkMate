package com.walkmate.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MdcFilter extends OncePerRequestFilter {

    private static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Note: traceId and spanId are automatically injected by Micrometer Tracing.
            // Here, we just add our custom business elements like userId.
            
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                MDC.put(MDC_USER_ID, jwt.getSubject()); // Assuming JWT subject is the User ID
            } else {
                MDC.put(MDC_USER_ID, "anonymous");
            }

            filterChain.doFilter(request, response);
        } finally {
            // Must clear MDC at the end to prevent memory leaks and incorrect context in thread pools
            MDC.remove(MDC_USER_ID);
        }
    }
}
