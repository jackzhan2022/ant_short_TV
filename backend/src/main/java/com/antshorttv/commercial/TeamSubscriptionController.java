package com.antshorttv.commercial;

import com.antshorttv.common.ApiResponse;
import java.util.List;
import com.antshorttv.security.TenantContextResolver;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/commercial/subscription")
public class TeamSubscriptionController {
    private final TeamSubscriptionQueryService service;
    private final TenantContextResolver tenantContextResolver;

    public TeamSubscriptionController(TeamSubscriptionQueryService service, TenantContextResolver tenantContextResolver) { this.service = service; this.tenantContextResolver = tenantContextResolver; }

    @GetMapping("/current")
    public ApiResponse<TeamSubscriptionEntity> current(@PathVariable Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        return ApiResponse.success(service.current(tenantId));
    }

    @GetMapping("/queued")
    public ApiResponse<List<TeamSubscriptionEntity>> queued(@PathVariable Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        return ApiResponse.success(service.queued(tenantId));
    }

    @GetMapping("/grants")
    public ApiResponse<List<CommercialEntitlementGrantEntity>> grants(@PathVariable Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        return ApiResponse.success(service.grants(tenantId));
    }
}
