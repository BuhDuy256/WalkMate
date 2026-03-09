package com.walkmate.infrastructure.logging;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;

@Aspect
@Component
@Slf4j
public class LayerLoggingAspect {

    /**
     * Log request in/out for Controller layer
     */
    @Around("within(com.walkmate.controller..*)")
    public Object logControllerAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant start = Instant.now();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        HttpServletRequest request = null;
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            request = attributes.getRequest();
        }

        if (request != null) {
            log.info("REST REQUEST | {} {} | Method: {}.{}", 
                    request.getMethod(), request.getRequestURI(), className, methodName);
        } else {
            log.info("REST REQUEST | Method: {}.{}", className, methodName);
        }

        try {
            Object result = joinPoint.proceed();
            long elapsedTime = Duration.between(start, Instant.now()).toMillis();
            
            if (request != null) {
                log.info("REST RESPONSE | {} {} | STATUS: OK | Time: {}ms", 
                        request.getMethod(), request.getRequestURI(), elapsedTime);
            } else {
                log.info("REST RESPONSE | Method: {}.{} | STATUS: OK | Time: {}ms", className, methodName, elapsedTime);
            }
            return result;
        } catch (Throwable e) {
            long elapsedTime = Duration.between(start, Instant.now()).toMillis();
            if (request != null) {
                log.warn("REST EXCEPTION | {} {} | STATUS: ERROR | Time: {}ms | Error: {}", 
                        request.getMethod(), request.getRequestURI(), elapsedTime, e.getMessage());
            } else {
                log.warn("REST EXCEPTION | Method: {}.{} | STATUS: ERROR | Time: {}ms | Error: {}", 
                        className, methodName, elapsedTime, e.getMessage());
            }
            throw e; // Let the GlobalExceptionHandler handle it
        }
    }

    /**
     * Log request in/out for Application (Service) layer
     */
    @Around("within(com.walkmate.application..*)")
    public Object logApplicationAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        log.debug("SERVICE ENTER | {}.{}", className, methodName);
        
        Instant start = Instant.now();
        try {
            Object result = joinPoint.proceed();
            long elapsedTime = Duration.between(start, Instant.now()).toMillis();
            log.debug("SERVICE EXIT  | {}.{} | Time: {}ms", className, methodName, elapsedTime);
            return result;
        } catch (Throwable e) {
            long elapsedTime = Duration.between(start, Instant.now()).toMillis();
            log.debug("SERVICE ERROR | {}.{} | Time: {}ms | Error: {}", className, methodName, elapsedTime, e.getMessage());
            throw e;
        }
    }
}
