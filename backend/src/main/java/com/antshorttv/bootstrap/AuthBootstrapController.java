package com.antshorttv.bootstrap;

import com.antshorttv.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthBootstrapController {

    private final AuthBootstrapService authBootstrapService;

    public AuthBootstrapController(AuthBootstrapService authBootstrapService) {
        this.authBootstrapService = authBootstrapService;
    }

    @GetMapping("/api/auth/bootstrap")
    public ApiResponse<AuthBootstrapResponse> bootstrap(
        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId
    ) {
        return ApiResponse.success(authBootstrapService.bootstrap(tenantId));
    }
}
