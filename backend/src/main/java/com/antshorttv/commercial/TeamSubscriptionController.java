package com.antshorttv.commercial;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.rbac.RequirePermission;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/commercial/subscription")
public class TeamSubscriptionController {
    private final TeamSubscriptionQueryService service;

    public TeamSubscriptionController(TeamSubscriptionQueryService service) { this.service = service; }

    @GetMapping("/current")
    @RequirePermission("BILLING:MANAGE")
    public ApiResponse<TeamSubscriptionEntity> current(@PathVariable Long tenantId) {
        return ApiResponse.success(service.current(tenantId));
    }

    @GetMapping("/queued")
    @RequirePermission("BILLING:MANAGE")
    public ApiResponse<List<TeamSubscriptionEntity>> queued(@PathVariable Long tenantId) {
        return ApiResponse.success(service.queued(tenantId));
    }

    @GetMapping("/grants")
    @RequirePermission("BILLING:MANAGE")
    public ApiResponse<List<CommercialEntitlementGrantEntity>> grants(@PathVariable Long tenantId) {
        return ApiResponse.success(service.grants(tenantId));
    }
}
