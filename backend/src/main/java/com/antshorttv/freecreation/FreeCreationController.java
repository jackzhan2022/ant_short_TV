package com.antshorttv.freecreation;

import com.antshorttv.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/free-creations")
public class FreeCreationController {
    private final FreeCreationService service;
    public FreeCreationController(FreeCreationService service) {this.service=service;}
    @GetMapping public ApiResponse<Map<String,Object>> list(HttpServletRequest request,
        @RequestParam(defaultValue="mine") String scope,@RequestParam(required=false) String mediaType,
        @RequestParam(required=false) String status,@RequestParam(required=false) String keyword,
        @RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.success(service.list(TenantRequestSupport.tenantId(request),scope,mediaType,status,keyword,current,pageSize));
    }
    @PostMapping public ApiResponse<Map<String,Object>> create(HttpServletRequest request,@Valid @RequestBody FreeCreationRequest body) {
        return ApiResponse.success(service.create(TenantRequestSupport.tenantId(request),body));
    }
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> detail(HttpServletRequest request,@PathVariable Long id) {
        return ApiResponse.success(service.detail(TenantRequestSupport.tenantId(request),id));
    }
    @GetMapping("/{id}/outputs/{outputId}/download") public ApiResponse<Map<String,Object>> download(HttpServletRequest request,@PathVariable Long id,@PathVariable Long outputId) {
        return ApiResponse.success(service.download(TenantRequestSupport.tenantId(request),id,outputId));
    }
}
