package com.antshorttv.user;

import com.antshorttv.auth.AuthService;
import com.antshorttv.auth.LoginByMobileRequest;
import com.antshorttv.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/currentUser")
    public ApiResponse<CurrentUserResponse> currentUser() {
        UserProfileResponse user = authService.currentUser();
        return ApiResponse.success(new CurrentUserResponse(
            user.nickname(),
            user.avatar(),
            String.valueOf(user.id()),
            user.email(),
            user.mobile(),
            "",
            "创作团队成员",
            "Ant Short TV",
            "user"
        ));
    }

    @PostMapping("/login/account")
    public LoginResponse loginAccount(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String type = request.type() == null || request.type().isBlank() ? "account" : request.type();
        authService.login(new LoginByMobileRequest(request.username(), request.password()), servletRequest);
        return new LoginResponse("ok", type, "user");
    }

    @PostMapping("/login/outLogin")
    public ApiResponse<Void> outLogin() {
        return ApiResponse.ok();
    }
}
