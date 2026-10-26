package com.antshorttv.avatar;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.rbac.RequireProjectPermission;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/virtual-avatar-tasks")
public class VirtualAvatarController {
    private final VirtualAvatarService service;
    public VirtualAvatarController(VirtualAvatarService service) {this.service=service;}
    public record Create(List<Long> variantIds) {}
    @PostMapping
    @RequireProjectPermission("ELEMENT:EDIT")
    public ApiResponse<List<Map<String,Object>>> create(@PathVariable long projectId,@RequestBody Create body,HttpServletRequest request) {
        return ApiResponse.success(service.create(TenantRequestSupport.tenantId(request),projectId,body.variantIds()));
    }
    @GetMapping("/candidates")
    @RequireProjectPermission("ELEMENT:VIEW")
    public ApiResponse<List<Map<String,Object>>> candidates(@PathVariable long projectId,@RequestParam(defaultValue="1") int page,@RequestParam(required=false) Long variantId,HttpServletRequest request) {
        return ApiResponse.success(service.candidates(TenantRequestSupport.tenantId(request),projectId,page,variantId));
    }
    @GetMapping("/{id}")
    @RequireProjectPermission("ELEMENT:VIEW")
    public ApiResponse<Map<String,Object>> get(@PathVariable long projectId,@PathVariable long id,HttpServletRequest request) {
        return ApiResponse.success(service.get(TenantRequestSupport.tenantId(request),projectId,id));
    }
    @PostMapping("/{id}/retry")
    @RequireProjectPermission("ELEMENT:EDIT")
    public ApiResponse<Map<String,Object>> retry(@PathVariable long projectId,@PathVariable long id,HttpServletRequest request) {
        return ApiResponse.success(service.retry(TenantRequestSupport.tenantId(request),projectId,id));
    }
}
