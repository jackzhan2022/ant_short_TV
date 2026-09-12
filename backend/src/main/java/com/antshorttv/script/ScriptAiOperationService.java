package com.antshorttv.script;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ScriptAiOperationService {
    @org.springframework.beans.factory.annotation.Autowired
    private AssetExtractionCoordination assetCoordination;
    private final ScriptAiOperationMapper operationMapper;
    private final AiExecutionService executionService;
    private final AiExecutionResponseMapper responseMapper;
    private final ProjectAiConfigService projectAiConfigService;
    private final ObjectMapper objectMapper;
    private final StoryboardGenerationAdmissionRepository storyboardAdmission;

    ScriptAiOperationService(
        ScriptAiOperationMapper operationMapper,
        AiExecutionService executionService,
        AiExecutionResponseMapper responseMapper,
        ProjectAiConfigService projectAiConfigService,
        ObjectMapper objectMapper,
        StoryboardGenerationAdmissionRepository storyboardAdmission
    ) {
        this.operationMapper = operationMapper;
        this.executionService = executionService;
        this.responseMapper = responseMapper;
        this.projectAiConfigService = projectAiConfigService;
        this.objectMapper = objectMapper;
        this.storyboardAdmission = storyboardAdmission;
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
        if (resumableInput instanceof ScopedAssetReextractionRequest request) {
            resumableInput = new ScopedAssetReextractionRequest(
                request.targetType().trim().toUpperCase(java.util.Locale.ROOT),
                request.promptPolicy().trim().toUpperCase(java.util.Locale.ROOT));
        }
        ScriptAiOperationEntity existing = operationMapper.selectByIdempotency(
            context.tenantId(), operationType, idempotencyKey
        );
        if (existing != null && existing.executionId != null) {
            if ("SCOPED_ASSET_REEXTRACTION".equals(operationType)
                && (!java.util.Objects.equals(existing.createdBy,context.userId())
                    || !java.util.Objects.equals(existing.projectId,projectId)
                    || !java.util.Objects.equals(existing.scriptId,scriptId)
                    || !java.util.Objects.equals(existing.redactedInputJson,writeJson(resumableInput)))) {
                throw new com.antshorttv.common.BusinessException(com.antshorttv.common.ErrorCode.VALIDATION_ERROR,
                    "该幂等标识已用于其他资产提取请求，请使用新的请求标识。");
            }
            return responseMapper.toResponse(executionService.requireTask(existing.executionId));
        }

        Long frozenModel = projectAiConfigService.resolveModelId(context.tenantId(), projectId, "TEXT");
        String assetFingerprint = null;
        if ("SCOPED_ASSET_REEXTRACTION".equals(operationType)) {
            if (frozenModel == null) throw new IllegalStateException("资产重提取缺少冻结文本模型。");
            assetFingerprint = assetCoordination.fingerprint(context.tenantId(),projectId,scriptId,
                context.userId(),scriptVersionId,frozenModel,writeJson(resumableInput));
            Long admitted = assetCoordination.admit(context.tenantId(),projectId,scriptId,assetFingerprint);
            if(admitted!=null) return responseMapper.toResponse(executionService.requireTaskForUpdate(admitted));
        }

        StoryboardGenerationAdmissionRepository.Admission admission = null;
        String admissionOrigin = traceId != null && traceId.startsWith("auto-storyboard-")
            ? "AUTO" : "MANUAL";
        if (AiBusinessScene.STORYBOARD_BREAKDOWN == scene
            && resumableInput instanceof StoryboardBreakdownRequest storyboardRequest) {
            admission = storyboardAdmission.admit(
                context.tenantId(), projectId, storyboardRequest.episodeId(), admissionOrigin);
            if (admission.executionId() != null) {
                AiExecutionTaskEntity admitted = executionService.requireTask(admission.executionId());
                if ("PENDING".equals(admitted.status) || "RUNNING".equals(admitted.status)) {
                    return responseMapper.toResponse(admitted);
                }
                storyboardAdmission.reopen(context.tenantId(), projectId,
                    storyboardRequest.episodeId(), admission.sourceFingerprint(), admitted.id,
                    admissionOrigin);
            }
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

        Long modelId = frozenModel;
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
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE),
            Map.of("operationType", operationType)
        );
        operation.executionId = execution.id;
        operation.updatedAt = LocalDateTime.now();
        operationMapper.updateById(operation);
        if(assetFingerprint!=null) assetCoordination.attach(context.tenantId(),projectId,scriptId,execution.id,assetFingerprint);
        if (admission != null && resumableInput instanceof StoryboardBreakdownRequest storyboardRequest) {
            storyboardAdmission.attach(context.tenantId(), projectId, storyboardRequest.episodeId(),
                admission.sourceFingerprint(), execution.id);
        }
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
