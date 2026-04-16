package com.walkmate.infrastructure.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String user = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
                    ? auth.getName()
                    : "anonymous";

            if (status >= 500) {
                log.error("User: {} | {} {} -> {} | {}ms",
                        user, request.getMethod(), request.getRequestURI(), status, duration);
            } else if (status >= 400) {
                log.warn("User: {} | {} {} -> {} | {}ms",
                        user, request.getMethod(), request.getRequestURI(), status, duration);
            } else {
                log.info("User: {} | {} {} -> {} | {}ms",
                        user, request.getMethod(), request.getRequestURI(), status, duration);
            }
        }
    }
}
