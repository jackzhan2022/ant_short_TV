package com.antshorttv.commercial;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.rbac.RequirePermission;
import com.antshorttv.security.CurrentPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/commercial/orders")
public class CommercialOrderController {
    private final CommercialOrderService service;
    private final CurrentPrincipal principal;
    public CommercialOrderController(CommercialOrderService service, CurrentPrincipal principal) { this.service = service; this.principal = principal; }

    @PostMapping
    @RequirePermission(value = "BILLING:MANAGE")
    public ApiResponse<CommercialOrderResponse> create(@PathVariable Long tenantId, @RequestBody CommercialOrderCreateRequest request) {
        return ApiResponse.success(service.create(new CommercialOrderCommand(tenantId, principal.require().userId(), request.packageVersionId())));
    }
    @GetMapping("/{orderId}")
    @RequirePermission(value = "BILLING:MANAGE")
    public ApiResponse<CommercialOrderEntity> detail(@PathVariable Long tenantId, @PathVariable Long orderId) { CommercialOrderEntity order = service.require(orderId); if (!tenantId.equals(order.tenantId)) throw new IllegalArgumentException("Order tenant mismatch"); return ApiResponse.success(order); }
}
record CommercialOrderCreateRequest(Long packageVersionId) {}
