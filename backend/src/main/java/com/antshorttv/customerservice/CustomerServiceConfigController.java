package com.antshorttv.customerservice;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CustomerServiceConfigController {
    private final CustomerServiceConfigService service;

    public CustomerServiceConfigController(CustomerServiceConfigService service) { this.service = service; }

    @GetMapping("/api/public/customer-service")
    public ResponseEntity<ApiResponse<CustomerServiceConfigService.Config>> publicConfig() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.current()));
    }

    @GetMapping("/api/platform/customer-service")
    @RequirePlatformPermission("PLATFORM_CUSTOMER_SERVICE_VIEW")
    public ApiResponse<CustomerServiceConfigService.Config> current() { return ApiResponse.success(service.current()); }

    @PutMapping(value = "/api/platform/customer-service", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePlatformPermission("PLATFORM_CUSTOMER_SERVICE_EDIT")
    public ApiResponse<CustomerServiceConfigService.Config> save(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(service.save(file));
    }

    @DeleteMapping("/api/platform/customer-service")
    @RequirePlatformPermission("PLATFORM_CUSTOMER_SERVICE_EDIT")
    public ApiResponse<CustomerServiceConfigService.Config> remove() { return ApiResponse.success(service.remove()); }
}
