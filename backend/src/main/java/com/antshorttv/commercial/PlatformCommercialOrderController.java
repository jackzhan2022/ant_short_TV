package com.antshorttv.commercial;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    @RequirePlatformPermission("PLATFORM_COMMERCIAL_ORDER_VIEW")
    public ApiResponse<PlatformCommercialOrderPageResponse> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String packageType,
        @RequestParam(required = false) Integer current,
        @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(orderService.listPlatform(
            PlatformCommercialOrderQuery.of(keyword, status, packageType, current, pageSize)));
    }

    @GetMapping("/{orderId}")
    @RequirePlatformPermission("PLATFORM_COMMERCIAL_ORDER_VIEW")
    public ApiResponse<PlatformCommercialOrderDetailResponse> detail(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.platformDetail(orderId));
    }

    @PostMapping("/{orderId}/reconcile")
    @RequirePlatformPermission("PLATFORM_COMMERCIAL_SUBSCRIPTION_ADJUST")
    public ApiResponse<CommercialOrderResponse> reconcile(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.response(lifecycleService.reconcile(orderId)));
    }
}
