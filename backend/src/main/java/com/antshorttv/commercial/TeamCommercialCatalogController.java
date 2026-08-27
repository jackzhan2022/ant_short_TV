package com.antshorttv.commercial;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.security.TenantContextResolver;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/commercial/catalog")
public class TeamCommercialCatalogController {
    private final CommercialPackageService service;
    private final TenantContextResolver tenantContextResolver;
    public TeamCommercialCatalogController(CommercialPackageService service, TenantContextResolver tenantContextResolver) { this.service = service; this.tenantContextResolver = tenantContextResolver; }
    @GetMapping
    public ApiResponse<List<CommercialCatalogItemResponse>> list(@PathVariable Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        return ApiResponse.success(service.listForSale(LocalDateTime.now()));
    }
}
