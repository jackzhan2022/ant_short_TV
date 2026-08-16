package com.antshorttv.auth;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.user.UserProfileResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    public ApiResponse<AuthSessionResponse> register(
        @Valid @RequestBody RegisterRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(authService.register(request, servletRequest));
    }

    @PostMapping("/auth/login")
    public ApiResponse<AuthSessionResponse> login(
        @Valid @RequestBody LoginByMobileRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(authService.login(request, servletRequest));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(HttpServletRequest servletRequest) {
        authService.logout(servletRequest);
        return ApiResponse.ok();
    }

    @GetMapping("/user/me")
    public ApiResponse<UserProfileResponse> currentUser() {
        return ApiResponse.success(authService.currentUser());
    }
}
