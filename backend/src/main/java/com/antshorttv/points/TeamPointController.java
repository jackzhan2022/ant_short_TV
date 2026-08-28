package com.antshorttv.points;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.security.TenantContextResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TeamPointController {

    private final TeamPointService teamPointService;
    private final AiPointSettlementService settlementService;
    private final TenantContextResolver tenantContextResolver;

    public TeamPointController(
        TeamPointService teamPointService,
        AiPointSettlementService settlementService,
        TenantContextResolver tenantContextResolver
    ) {
        this.teamPointService = teamPointService;
        this.settlementService = settlementService;
        this.tenantContextResolver = tenantContextResolver;
    }

    @GetMapping("/tenants/{tenantId}/points/account")
    public ApiResponse<TeamPointAccountResponse> account(@PathVariable Long tenantId) {
        return ApiResponse.success(teamPointService.account(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/points/transactions")
    public ApiResponse<TeamPointTransactionPageResponse> transactions(
        @PathVariable Long tenantId,
        @RequestParam(defaultValue = "1") Integer current,
        @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        return ApiResponse.success(teamPointService.transactions(tenantId, current, pageSize));
    }

    @GetMapping("/tenants/{tenantId}/points/reconciliation")
    public ApiResponse<AiPointReconciliation> reconciliation(@PathVariable Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        return ApiResponse.success(settlementService.reconcile(tenantId));
    }
}
