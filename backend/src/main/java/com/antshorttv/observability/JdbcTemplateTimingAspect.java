package com.antshorttv.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class JdbcTemplateTimingAspect {
    @Around("execution(* org.springframework.jdbc.core.JdbcTemplate.*(..))")
    public Object recordJdbcTemplateCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            DatabaseQueryObservation.recordQuery(System.nanoTime() - started);
        }
    }
}
