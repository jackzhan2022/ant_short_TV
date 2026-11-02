package com.antshorttv.security;

import com.antshorttv.authsession.AuthSessionCookieService;
import com.antshorttv.authsession.AuthSessionService;
import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.common.ApiResponse;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final AuthSessionCookieService cookieService;
    private final AuthSessionService sessionService;
    private final OperationLogService logs;
    private final ObjectMapper objectMapper;

    public SessionAuthenticationFilter(
        AuthSessionCookieService cookieService,
        AuthSessionService sessionService,
        OperationLogService logs,
        ObjectMapper objectMapper
    ) {
        this.cookieService = cookieService;
        this.sessionService = sessionService;
        this.logs = logs;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String credential = cookieService.read(request).orElse(null);
        AuthenticatedUser principal = sessionService.authenticate(credential).orElse(null);
        String expectedSession = request.getHeader("X-Auth-Session-Id");
        String actualSession = principal == null ? null : principal.sessionId();
        if (actualSession == null && "/api/auth/impersonation/stop".equals(request.getRequestURI())) {
            actualSession = sessionService.ownedSession(credential).map(session -> session.getSessionId()).orElse(null);
        }
        if (expectedSession != null && actualSession != null && !expectedSession.equals(actualSession)) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            objectMapper.writeValue(response.getWriter(), ApiResponse.fail("AUTH_SESSION_CHANGED", "登录身份已在其他页面切换，请刷新页面后重试。"));
            return;
        }
        if (principal != null) authenticate(principal);
        boolean completed = false;
        try {
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            if (principal != null && principal.impersonation() != null && request.getRequestURI().startsWith("/api/")
                && !List.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod())) {
                Long tenantId = principal.impersonation().tenantId();
                String selectedTenant = request.getHeader("X-Tenant-Id");
                if (selectedTenant != null && selectedTenant.matches("[0-9]{1,18}")) tenantId = Long.valueOf(selectedTenant);
                logs.record(principal.impersonation().administratorId(), tenantId, "IMPERSONATION_REQUEST",
                    principal.userId(), completed && response.getStatus() < 400 ? OperationResult.SUCCESS : OperationResult.FAILURE,
                    request, Map.of("administratorId", principal.impersonation().administratorId(), "targetUserId", principal.userId(),
                        "sessionId", principal.sessionId(), "method", request.getMethod(), "path", request.getRequestURI(),
                        "httpStatus", response.getStatus()));
            }
        }
    }

    private void authenticate(AuthenticatedUser principal) {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
