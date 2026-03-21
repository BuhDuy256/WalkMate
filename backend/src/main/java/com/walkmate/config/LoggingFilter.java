package com.walkmate.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        System.out.println("============================================");
        System.out.println("[REQUEST] " + httpRequest.getMethod() + " " + httpRequest.getRequestURI());
        System.out.println("[HEADERS] Content-Type: " + httpRequest.getContentType());
        System.out.println("[HEADERS] Content-Length: " + httpRequest.getContentLength());
        System.out.println("============================================");

        chain.doFilter(request, response);

        System.out.println("[RESPONSE] Status: " + httpResponse.getStatus());
        System.out.println("============================================");
    }
}
