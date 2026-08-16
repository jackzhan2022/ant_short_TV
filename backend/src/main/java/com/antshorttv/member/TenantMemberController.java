package com.antshorttv.member;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.tenant.TenantSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}")
public class TenantMemberController {

    private final TenantMemberService tenantMemberService;

    public TenantMemberController(TenantMemberService tenantMemberService) {
        this.tenantMemberService = tenantMemberService;
    }

    @GetMapping("/members")
    public ApiResponse<List<TenantMemberResponse>> list(@PathVariable Long tenantId) {
        return ApiResponse.success(tenantMemberService.list(tenantId));
    }

    @DeleteMapping("/members/{memberId}")
    public ApiResponse<Void> remove(
        @PathVariable Long tenantId,
        @PathVariable Long memberId,
        HttpServletRequest request
    ) {
        return tenantMemberService.remove(tenantId, memberId, request);
    }

    @PostMapping("/members/leave")
    public ApiResponse<Void> leave(@PathVariable Long tenantId, HttpServletRequest request) {
        return tenantMemberService.leave(tenantId, request);
    }

    @PostMapping("/transfer-owner")
    public ApiResponse<TenantSummaryResponse> transferOwner(
        @PathVariable Long tenantId,
        @Valid @RequestBody TransferOwnerRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(tenantMemberService.transferOwner(tenantId, request, servletRequest));
    }
}
