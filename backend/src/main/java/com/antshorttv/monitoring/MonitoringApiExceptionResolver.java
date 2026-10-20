package com.antshorttv.monitoring;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.CurrentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.EnumSet;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/** Observes exceptions before the existing advice without resolving them or changing responses. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MonitoringApiExceptionResolver implements HandlerExceptionResolver {
    private static final EnumSet<ErrorCode> TECHNICAL = EnumSet.of(
        ErrorCode.AI_AUTH_FAILED, ErrorCode.AI_RATE_LIMIT, ErrorCode.AI_QUOTA_EXCEEDED,
        ErrorCode.AI_PROVIDER_TIMEOUT, ErrorCode.AI_PROVIDER_ERROR, ErrorCode.AI_RESPONSE_INVALID,
        ErrorCode.AI_IMAGE_SERVICE_UNAVAILABLE, ErrorCode.AI_VIDEO_SERVICE_UNAVAILABLE,
        ErrorCode.AI_VOICE_SERVICE_UNAVAILABLE, ErrorCode.WORKFLOW_SKILL_STORAGE_ERROR,
        ErrorCode.WORKFLOW_AGENT_TIMEOUT, ErrorCode.ANALYSIS_AGENT_INCOMPLETE,
        ErrorCode.REQUIRED_TOOL_NOT_CALLED);
    private final MonitoringBuffer buffer;
    private final CurrentPrincipal principal;

    public MonitoringApiExceptionResolver(MonitoringBuffer buffer, CurrentPrincipal principal) {
        this.buffer = buffer;
        this.principal = principal;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        if (!request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/api/monitoring/")) return null;
        if (exception instanceof BusinessException business && !TECHNICAL.contains(business.getErrorCode())) return null;
        if (exception instanceof jakarta.validation.ConstraintViolationException
            || exception instanceof org.springframework.http.converter.HttpMessageNotReadableException
            || exception instanceof org.springframework.beans.TypeMismatchException) return null;
        if (exception instanceof ErrorResponse error && error.getStatusCode().value() < 500) return null;
        if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) return null;
        // DispatcherServlet can redispatch the same request after an error.
        if (request.getAttribute(MonitoringApiExceptionResolver.class.getName()) != null) return null;
        try {
            request.setAttribute(MonitoringApiExceptionResolver.class.getName(), true);
            Long userId = principal.get().map(user -> user.userId()).orElse(null);
            String code = exception instanceof BusinessException business ? business.getErrorCode().name() : exception.getClass().getSimpleName();
            StringBuilder stack = new StringBuilder();
            for (var frame : exception.getStackTrace()) {
                if (stack.length() >= 800) break;
                stack.append(frame).append('\n');
            }
            buffer.record(new MonitoringEvent(Instant.now(), "API", code,
                request.getMethod() + " " + request.getRequestURI(), exception.getMessage(), stack.toString(),
                userId, tenantHeader(request.getHeader("X-Tenant-Id")), null, null));
        } catch (RuntimeException ignored) {
            // Monitoring must never replace the original exception.
        }
        return null;
    }

    static Long tenantHeader(String value) {
        try {
            Long id = Long.valueOf(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException ignored) { return null; }
    }
}
