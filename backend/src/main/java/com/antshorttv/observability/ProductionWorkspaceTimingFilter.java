package com.antshorttv.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ProductionWorkspaceTimingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ProductionWorkspaceTimingFilter.class);
    private static final Pattern SELECTED_ROUTE = Pattern.compile(
        "^/api/projects/\\d+/(script-page-workspace|asset-settings-summary|storyboard-workspace|script-analysis/current)$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod()) || !SELECTED_ROUTE.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        long started = System.nanoTime();
        try (var ignored = DatabaseQueryObservation.open()) {
            chain.doFilter(request, response);
            long[] query = DatabaseQueryObservation.snapshot();
            log.info("production_workspace_timing route={} status={} http_ms={} sql_count={} sql_ms={}",
                normalizedRoute(request.getRequestURI()), response.getStatus(), nanosToMillis(System.nanoTime() - started),
                query[0], nanosToMillis(query[1]));
        }
    }

    private String normalizedRoute(String route) {
        return route.replaceFirst("^/api/projects/\\d+", "/api/projects/{projectId}");
    }

    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
