package com.antshorttv.commercial;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.rbac.RequirePermission;
import com.antshorttv.security.CurrentPrincipal;
import org.springframework.web.bind.annotation.*;
import com.antshorttv.security.TenantContextResolver;

@RestController
@RequestMapping("/api/tenants/{tenantId}/commercial/orders")
public class CommercialOrderController {
    private final CommercialOrderService service;
    private final CommercialPaymentLifecycleService lifecycleService;
    private final CurrentPrincipal principal;
    private final TenantContextResolver tenantContextResolver;
    public CommercialOrderController(CommercialOrderService service, CommercialPaymentLifecycleService lifecycleService, CurrentPrincipal principal, TenantContextResolver tenantContextResolver) { this.service = service; this.lifecycleService = lifecycleService; this.principal = principal; this.tenantContextResolver = tenantContextResolver; }

    @PostMapping
    @RequirePermission(value = "BILLING:MANAGE")
    public ApiResponse<CommercialOrderResponse> create(@PathVariable Long tenantId, @RequestBody CommercialOrderCreateRequest request) {
        return ApiResponse.success(service.create(new CommercialOrderCommand(tenantId, principal.require().userId(), request.packageVersionId())));
    }
    @GetMapping
    public ApiResponse<java.util.List<CommercialOrderResponse>> active(@PathVariable Long tenantId) { tenantContextResolver.requireActiveMember(tenantId); return ApiResponse.success(service.active(tenantId)); }
    @GetMapping("/{orderId}")
    public ApiResponse<CommercialOrderEntity> detail(@PathVariable Long tenantId, @PathVariable Long orderId) { tenantContextResolver.requireActiveMember(tenantId); CommercialOrderEntity order = service.require(orderId); if (!tenantId.equals(order.tenantId)) throw new IllegalArgumentException("Order tenant mismatch"); return ApiResponse.success(order); }
    @PostMapping("/{orderId}/refresh")
    @RequirePermission(value = "BILLING:MANAGE")
    public ApiResponse<CommercialOrderResponse> refresh(@PathVariable Long tenantId, @PathVariable Long orderId) { CommercialOrderEntity order = service.require(orderId); if (!tenantId.equals(order.tenantId)) throw new IllegalArgumentException("Order tenant mismatch"); return ApiResponse.success(service.response(lifecycleService.refresh(orderId))); }
}
record CommercialOrderCreateRequest(Long packageVersionId) {}
