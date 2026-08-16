package com.antshorttv.invitation;

import com.antshorttv.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TenantInvitationController {

    private final TenantInvitationService tenantInvitationService;

    public TenantInvitationController(TenantInvitationService tenantInvitationService) {
        this.tenantInvitationService = tenantInvitationService;
    }

    @PostMapping("/tenants/{tenantId}/invitations")
    public ApiResponse<TenantInvitationResponse> create(
        @PathVariable Long tenantId,
        @Valid @RequestBody CreateInvitationRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(tenantInvitationService.create(tenantId, request, servletRequest));
    }

    @GetMapping("/invitations")
    public ApiResponse<List<TenantInvitationResponse>> myInvitations() {
        return ApiResponse.success(tenantInvitationService.myInvitations());
    }

    @GetMapping("/invitations/{token}")
    public ApiResponse<TenantInvitationResponse> detail(@PathVariable String token) {
        return ApiResponse.success(tenantInvitationService.detail(token));
    }

    @PostMapping("/invitations/{token}/accept")
    public ApiResponse<TenantInvitationResponse> accept(
        @PathVariable String token,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(tenantInvitationService.accept(token, servletRequest));
    }

    @PostMapping("/invitations/{token}/reject")
    public ApiResponse<TenantInvitationResponse> reject(
        @PathVariable String token,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(tenantInvitationService.reject(token, servletRequest));
    }

    @PostMapping("/invitations/{id}/cancel")
    public ApiResponse<TenantInvitationResponse> cancel(
        @PathVariable Long id,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(tenantInvitationService.cancel(id, servletRequest));
    }
}
