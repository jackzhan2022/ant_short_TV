package com.antshorttv.script;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ScriptAiOperationService {
    private final ScriptAiOperationMapper operationMapper;
    private final AiExecutionService executionService;
    private final AiExecutionResponseMapper responseMapper;
    private final ProjectAiConfigService projectAiConfigService;
    private final ObjectMapper objectMapper;

    ScriptAiOperationService(
        ScriptAiOperationMapper operationMapper,
        AiExecutionService executionService,
        AiExecutionResponseMapper responseMapper,
        ProjectAiConfigService projectAiConfigService,
        ObjectMapper objectMapper
    ) {
        this.operationMapper = operationMapper;
        this.executionService = executionService;
        this.responseMapper = responseMapper;
        this.projectAiConfigService = projectAiConfigService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    AiExecutionResponse submit(
        TenantContext context,
        Long projectId,
        AiBusinessScene scene,
        String operationType,
        Long scriptId,
        Long scriptVersionId,
        Object resumableInput,
        String idempotencyKey,
        String traceId
    ) {
        ScriptAiOperationEntity existing = operationMapper.selectByIdempotency(
            context.tenantId(), operationType, idempotencyKey
        );
        if (existing != null && existing.executionId != null) {
            return responseMapper.toResponse(executionService.requireTask(existing.executionId));
        }

        LocalDateTime now = LocalDateTime.now();
        ScriptAiOperationEntity operation = new ScriptAiOperationEntity();
        operation.tenantId = context.tenantId();
        operation.projectId = projectId;
        operation.operationType = operationType;
        operation.scriptId = scriptId;
        operation.scriptVersionId = scriptVersionId;
        operation.redactedInputJson = writeJson(resumableInput);
        operation.idempotencyKey = idempotencyKey;
        operation.status = "PENDING";
        operation.createdBy = context.userId();
        operation.createdAt = now;
        operation.updatedAt = now;
        operationMapper.insert(operation);

        Long modelId = projectAiConfigService.resolveModelId(context.tenantId(), projectId, "TEXT");
        AiExecutionTaskEntity execution = executionService.createWithReservation(
            new AiExecutionCreateCommand(
                context.tenantId(),
                context.userId(),
                projectId,
                scene.code(),
                "TEXT",
                "SCRIPT_AI_OPERATION",
                operation.id,
                modelId,
                "SUBMIT",
                idempotencyKey,
                traceId,
                true,
                operation.redactedInputJson
            ),
            Map.of(),
            Map.of("operationType", operationType)
        );
        operation.executionId = execution.id;
        operation.updatedAt = LocalDateTime.now();
        operationMapper.updateById(operation);
        return responseMapper.toResponse(execution);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Script operation input cannot be serialized.", exception);
        }
    }

    void updateResult(ScriptAiOperationEntity operation) {
        operationMapper.updateById(operation);
    }
}
