package com.antshorttv.operations.telemetry;

import com.antshorttv.observability.DatabaseQueryObservation;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class OperationsHttpFilter extends OncePerRequestFilter {
    private final OperationsTelemetry telemetry;
    public OperationsHttpFilter(OperationsTelemetry telemetry) { this.telemetry=telemetry; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        if(!telemetry.enabled)return true;
        if(request.getContentType()!=null && request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("multipart/"))return true;
        String path=request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/platform/operations") || path.startsWith("/api/monitoring")
            || path.startsWith("/api/health") || path.startsWith("/api/public/health") || path.startsWith("/api/public/operations-health") || path.contains("/stream") || path.contains("/events/subscribe");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String requestId=UUID.randomUUID().toString();
        request.setAttribute("operations.requestId",requestId);response.setHeader("X-Request-Id",requestId);
        long started=System.nanoTime(); String failureOutcome=null;
        request.setAttribute("operations.startedNanos",started);
        try(var scope=DatabaseQueryObservation.open()) {
            try { chain.doFilter(request,response); }
            catch(IOException error) { failureOutcome="CANCELED";throw error; }
            catch(ServletException|RuntimeException error) { failureOutcome="TECHNICAL_ERROR";throw error; }
            finally {
                // Async/SSE/streaming completion is not comparable to interactive request latency.
                if(!request.isAsyncStarted() && !excludedResponse(response)) {
                    String route=String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
                    if(route.equals("null"))route="UNMATCHED";
                    long[] sql=DatabaseQueryObservation.snapshot(); int status=response.getStatus();
                    Object attributed=request.getAttribute("operations.outcome");
                    String outcome=failureOutcome!=null?failureOutcome:attributed!=null?String.valueOf(attributed):status>=500?"TECHNICAL_ERROR":status>=400?"BUSINESS_REJECTION":"SUCCESS";
                    telemetry.recordHttp(new OperationsTelemetry.HttpObservation(Instant.now(),route,request.getMethod(),outcome,
                        (System.nanoTime()-started)/1_000_000,requestId,sql[0],sql[1]/1_000_000));
                }
            }
        }
    }
    private boolean excludedResponse(HttpServletResponse response) {
        String type=String.valueOf(response.getContentType()).toLowerCase(java.util.Locale.ROOT);
        String disposition=response.getHeader("Content-Disposition");
        return type.startsWith("text/event-stream") || type.startsWith("video/") || type.startsWith("audio/") || type.startsWith("image/")
            || type.startsWith("application/zip") || type.startsWith("application/octet-stream")
            || (disposition!=null && disposition.toLowerCase(java.util.Locale.ROOT).contains("attachment"));
    }
}
