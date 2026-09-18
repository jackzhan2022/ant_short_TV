package com.antshorttv.aiimage;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.rbac.RequireProjectPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/asset-image-batches")
class AssetImageBatchController {
    private final AssetImageBatchService service;
    private final AssetImageBatchScheduler scheduler;

    AssetImageBatchController(AssetImageBatchService service, AssetImageBatchScheduler scheduler) {
        this.service = service;
        this.scheduler = scheduler;
    }

    @PostMapping("/preflight")
    @RequireProjectPermission({"ELEMENT:VIEW", "AI_IMAGE_TASK:VIEW"})
    ApiResponse<AssetImageBatchPreflightResponse> preflight(
        @PathVariable Long projectId,
        @Valid @RequestBody AssetImageBatchRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.preflight(tenantId(request), projectId, body));
    }

    @PostMapping
    @RequireProjectPermission({"AI_IMAGE_TASK:CREATE", "AI_SERVICE:USE"})
    ResponseEntity<ApiResponse<AssetImageBatchResponse>> create(
        @PathVariable Long projectId,
        @Valid @RequestBody AssetImageBatchRequest body,
        HttpServletRequest request
    ) {
        AssetImageBatchResponse response = service.create(
            tenantId(request), projectId, body, request.getHeader("Idempotency-Key"));
        scheduler.dispatchBatch(response.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success(service.getCreated(tenantId(request), projectId, response.id())));
    }

    @GetMapping("/{batchId}")
    @RequireProjectPermission("AI_IMAGE_TASK:VIEW")
    ApiResponse<AssetImageBatchResponse> get(
        @PathVariable Long projectId,
        @PathVariable Long batchId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.get(tenantId(request), projectId, batchId));
    }

    private Long tenantId(HttpServletRequest request) {
        return TenantRequestSupport.tenantId(request);
    }
}
