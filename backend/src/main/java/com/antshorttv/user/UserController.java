package com.antshorttv.user;

import com.antshorttv.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/currentUser")
    public ApiResponse<CurrentUserResponse> currentUser() {
        CurrentUserResponse user = new CurrentUserResponse(
            "admin",
            "https://gw.alipayobjects.com/zos/antfincdn/FLrTNDvlna/antDesignPro.svg",
            "00000001",
            "admin@antshorttv.local",
            "Ant Short TV administrator",
            "Administrator",
            "Platform",
            "admin"
        );
        return ApiResponse.success(user);
    }

    @PostMapping("/login/account")
    public LoginResponse loginAccount(@Valid @RequestBody LoginRequest request) {
        String type = request.type() == null || request.type().isBlank() ? "account" : request.type();
        return new LoginResponse("ok", type, "admin");
    }

    @PostMapping("/login/outLogin")
    public ApiResponse<Void> outLogin() {
        return ApiResponse.ok();
    }
}
