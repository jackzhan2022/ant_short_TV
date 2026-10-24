package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.execution.AiExecutionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/prop-simplification")
public class PropSimplificationController {
    private final PropSimplificationService service;
    public PropSimplificationController(PropSimplificationService service) {this.service=service;}
    @GetMapping
    public ApiResponse<JsonNode> status(@PathVariable long projectId,HttpServletRequest request) {
        return ApiResponse.success(service.status(TenantRequestSupport.tenantId(request),projectId));
    }
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AiExecutionResponse> analyze(@PathVariable long projectId,HttpServletRequest request) {
        String key=request.getHeader("Idempotency-Key");
        return ApiResponse.success(service.submit(TenantRequestSupport.tenantId(request),projectId,
            key==null||key.isBlank()?UUID.randomUUID().toString():key));
    }
    @PostMapping("/{planId}/apply")
    public ApiResponse<JsonNode> apply(@PathVariable long projectId,@PathVariable long planId,
        @RequestBody JsonNode decision,HttpServletRequest request) {
        return ApiResponse.success(service.apply(TenantRequestSupport.tenantId(request),projectId,planId,decision));
    }
    @PostMapping("/{planId}/undo")
    public ApiResponse<JsonNode> undo(@PathVariable long projectId,@PathVariable long planId,HttpServletRequest request) {
        return ApiResponse.success(service.undo(TenantRequestSupport.tenantId(request),projectId,planId));
    }
}
