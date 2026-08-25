package com.antshorttv.execution;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AiExecutionContractVersionFilter extends OncePerRequestFilter {
    static final String HEADER_NAME = "X-AI-Task-Contract-Version";
    static final String VERSION = "1";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().matches("/api/tenants/[^/]+/ai-executions(?:/.*)?");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        response.setHeader(HEADER_NAME, VERSION);
        filterChain.doFilter(request, response);
    }
}
