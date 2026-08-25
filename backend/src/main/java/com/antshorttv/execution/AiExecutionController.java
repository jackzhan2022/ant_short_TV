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
        accessService.requireControl(tenantId, executionId);
        return ApiResponse.success(responseMapper.toResponse(executionService.cancel(executionId)));
    }

    @PostMapping("/{executionId}/retry")
    public ApiResponse<AiExecutionResponse> retry(
        @PathVariable Long tenantId,
        @PathVariable Long executionId
    ) {
        accessService.requireControl(tenantId, executionId);
        return ApiResponse.success(responseMapper.toResponse(executionService.retry(executionId)));
    }

    @PostMapping("/{executionId}/regenerate")
    public ApiResponse<AiExecutionResponse> regenerate(
        @PathVariable Long tenantId,
        @PathVariable Long executionId,
        @Valid @RequestBody AiExecutionRegenerateRequest request
    ) {
        accessService.requireControl(tenantId, executionId);
        return ApiResponse.success(responseMapper.toResponse(executionService.regenerate(
            executionId,
            request.clientIdempotencyKey(),
            request.traceId()
        )));
    }
}
