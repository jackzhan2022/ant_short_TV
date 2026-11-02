package com.antshorttv.auth;

import com.antshorttv.authsession.AuthSessionCookieService;
import com.antshorttv.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImpersonationController {
    private final ImpersonationService service;
    private final AuthSessionCookieService cookies;

    public ImpersonationController(ImpersonationService service, AuthSessionCookieService cookies) {
        this.service = service;
        this.cookies = cookies;
    }

    @PostMapping("/api/platform/tenants/{tenantId:\\d+}/members/{memberId:\\d+}/impersonation")
    public ApiResponse<Map<String, Long>> start(@PathVariable Long tenantId, @PathVariable Long memberId,
        @Valid @RequestBody StartRequest body, HttpServletRequest request, HttpServletResponse response) {
        var issued = service.start(tenantId, memberId, body.code(), body.reason(), request);
        cookies.write(response, issued);
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success(Map.of("tenantId", tenantId));
    }

    @PostMapping("/api/auth/impersonation/stop")
    public ApiResponse<Void> stop(HttpServletRequest request, HttpServletResponse response) {
        var issued = service.stop(request);
        cookies.write(response, issued);
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok();
    }

    public record StartRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String code,
        @NotBlank @Size(max = 500) String reason) {}
}
