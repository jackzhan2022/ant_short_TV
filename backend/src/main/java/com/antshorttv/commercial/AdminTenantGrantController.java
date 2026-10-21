package com.antshorttv.commercial;

import com.antshorttv.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/tenants")
public class AdminTenantGrantController {
    private final AdminTenantGrantService service;
    public AdminTenantGrantController(AdminTenantGrantService service) { this.service=service; }

    @GetMapping("/grant-options")
    public ApiResponse<List<CommercialCatalogItemResponse>> options() { return ApiResponse.success(service.options()); }

    @PostMapping("/{tenantId:\\d+}/package-grants")
    public ApiResponse<AdminTenantGrantService.Result> grant(@PathVariable Long tenantId,
        @Valid @RequestBody AdminTenantGrantService.Command command, HttpServletRequest request) {
        return ApiResponse.success(service.grant(tenantId, command, request));
    }
}
