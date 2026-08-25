package com.antshorttv.security;

import com.antshorttv.authsession.AuthSessionCookieService;
import com.antshorttv.authsession.AuthSessionService;
import com.antshorttv.authsession.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final AuthSessionCookieService cookieService;
    private final AuthSessionService sessionService;

    public SessionAuthenticationFilter(
        AuthSessionCookieService cookieService,
        AuthSessionService sessionService
    ) {
        this.cookieService = cookieService;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        cookieService.read(request)
            .flatMap(sessionService::authenticate)
            .ifPresent(this::authenticate);
        filterChain.doFilter(request, response);
    }

    private void authenticate(AuthenticatedUser principal) {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
