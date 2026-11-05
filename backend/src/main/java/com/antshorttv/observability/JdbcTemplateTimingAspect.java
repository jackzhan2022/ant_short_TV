package com.antshorttv.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class JdbcTemplateTimingAspect {
    @Around("execution(* org.springframework.jdbc.core.JdbcTemplate.query*(..)) || execution(* org.springframework.jdbc.core.JdbcTemplate.update(..)) || execution(* org.springframework.jdbc.core.JdbcTemplate.batchUpdate(..)) || execution(* org.springframework.jdbc.core.JdbcTemplate.execute(..))")
    public Object recordJdbcTemplateCall(ProceedingJoinPoint joinPoint) throws Throwable {
        try (var ignored = DatabaseQueryObservation.query()) {
            return joinPoint.proceed();
        }
    }
}
