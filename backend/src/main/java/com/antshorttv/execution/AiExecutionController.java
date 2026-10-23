package com.antshorttv.execution;

import com.antshorttv.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/ai-executions")
public class AiExecutionController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.antshorttv.freecreation.FreeCreationService freeCreations;
    private final AiExecutionService executionService;
    private final AiExecutionAccessService accessService;
    private final AiExecutionResponseMapper responseMapper;

    public AiExecutionController(
        AiExecutionService executionService,
        AiExecutionAccessService accessService,
        AiExecutionResponseMapper responseMapper
    ) {
        this.executionService = executionService;
        this.accessService = accessService;
        this.responseMapper = responseMapper;
    }

    @GetMapping("/{executionId}")
    public ApiResponse<AiExecutionResponse> detail(
        @PathVariable Long tenantId,
        @PathVariable Long executionId
    ) {
        return ApiResponse.success(responseMapper.toResponse(accessService.requireView(tenantId, executionId)));
    }

    @PostMapping("/{executionId}/cancel")
    public ApiResponse<AiExecutionResponse> cancel(
        @PathVariable Long tenantId,
        @PathVariable Long executionId
    ) {
        var task=accessService.requireControl(tenantId, executionId);
        if ("FREE_CREATION".equals(task.businessType)) return freeControl(tenantId,task,"CANCEL",null);
        return ApiResponse.success(responseMapper.toResponse(executionService.cancel(executionId)));
    }

    @PostMapping("/{executionId}/retry")
    public ApiResponse<AiExecutionResponse> retry(
        @PathVariable Long tenantId,
        @PathVariable Long executionId,
        @org.springframework.web.bind.annotation.RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey
    ) {
        var task=accessService.requireControl(tenantId, executionId);
        if ("FREE_CREATION".equals(task.businessType)) return freeControl(tenantId,task,"RETRY",idempotencyKey);
        return ApiResponse.success(responseMapper.toResponse(executionService.retry(executionId)));
    }

    @PostMapping("/{executionId}/regenerate")
    public ApiResponse<AiExecutionResponse> regenerate(
        @PathVariable Long tenantId,
        @PathVariable Long executionId,
        @Valid @RequestBody AiExecutionRegenerateRequest request
    ) {
        var task=accessService.requireControl(tenantId, executionId);
        if ("FREE_CREATION".equals(task.businessType)) return freeControl(tenantId,task,"REGENERATE",request.clientIdempotencyKey());
        return ApiResponse.success(responseMapper.toResponse(executionService.regenerate(
            executionId,
            request.clientIdempotencyKey(),
            request.traceId()
        )));
    }

    private ApiResponse<AiExecutionResponse> freeControl(Long tenantId,AiExecutionTaskEntity task,String action,String key) {
        var result=freeCreations.control(tenantId,task.businessId,action,key);
        Long executionId=((Number)result.get("executionId")).longValue();
        return ApiResponse.success(responseMapper.toResponse(executionService.requireTask(executionId)));
    }
}
