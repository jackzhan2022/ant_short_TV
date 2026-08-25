package com.antshorttv.auth;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.authsession.AuthSessionCookieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final AuthSessionCookieService cookieService;

    public AuthController(AuthService authService, AuthSessionCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/auth/register")
    public ApiResponse<AuthSessionResponse> register(
        @Valid @RequestBody RegisterRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        AuthResult result = authService.register(request, servletRequest);
        cookieService.write(servletResponse, result.issuedSession());
        return ApiResponse.success(result.response());
    }

    @PostMapping("/auth/login")
    public ApiResponse<AuthSessionResponse> login(
        @Valid @RequestBody LoginByMobileRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        AuthResult result = authService.login(request, servletRequest);
        cookieService.write(servletResponse, result.issuedSession());
        return ApiResponse.success(result.response());
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        authService.logout(servletRequest);
        cookieService.clear(servletResponse);
        return ApiResponse.ok();
    }

}
