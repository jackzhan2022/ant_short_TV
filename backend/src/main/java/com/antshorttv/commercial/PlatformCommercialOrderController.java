package com.antshorttv.commercial;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/commercial/orders")
public class PlatformCommercialOrderController {
    private final CommercialPaymentLifecycleService lifecycleService;
    private final CommercialOrderService orderService;

    public PlatformCommercialOrderController(CommercialPaymentLifecycleService lifecycleService, CommercialOrderService orderService) {
        this.lifecycleService = lifecycleService;
        this.orderService = orderService;
    }

    @PostMapping("/{orderId}/reconcile")
    @RequirePlatformPermission("PLATFORM_COMMERCIAL_SUBSCRIPTION_ADJUST")
    public ApiResponse<CommercialOrderResponse> reconcile(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.response(lifecycleService.reconcile(orderId)));
    }
}
