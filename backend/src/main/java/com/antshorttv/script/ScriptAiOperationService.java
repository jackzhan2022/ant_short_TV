package com.antshorttv.script;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ScriptAiOperationService {
    private final ScriptAiOperationMapper operationMapper;
    private final AiExecutionService executionService;
    private final AiExecutionResponseMapper responseMapper;
    private final ProjectAiConfigService projectAiConfigService;
    private final ObjectMapper objectMapper;
    private final StoryboardGenerationAdmissionRepository storyboardAdmission;
    private final ScriptAssetExtractionCoordinationRepository assetExtractionCoordination;
    private final JdbcTemplate jdbc;

    ScriptAiOperationService(
        ScriptAiOperationMapper operationMapper,
        AiExecutionService executionService,
        AiExecutionResponseMapper responseMapper,
        ProjectAiConfigService projectAiConfigService,
        ObjectMapper objectMapper,
        StoryboardGenerationAdmissionRepository storyboardAdmission,
        ScriptAssetExtractionCoordinationRepository assetExtractionCoordination,
        JdbcTemplate jdbc
    ) {
        this.operationMapper = operationMapper;
        this.executionService = executionService;
        this.responseMapper = responseMapper;
        this.projectAiConfigService = projectAiConfigService;
        this.objectMapper = objectMapper;
        this.storyboardAdmission = storyboardAdmission;
        this.assetExtractionCoordination = assetExtractionCoordination;
        this.jdbc = jdbc;
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

        Long modelId = projectAiConfigService.resolveModelId(context.tenantId(), projectId, "TEXT");
        ScriptAssetExtractionCoordinationRepository.Admission assetAdmission = null;
        String assetFingerprint = null;
        if (AiBusinessScene.SCOPED_ASSET_REEXTRACTION == scene
            && resumableInput instanceof ScopedAssetReextractionRequest request) {
            assetFingerprint = assetFingerprint(context, projectId, scriptId, scriptVersionId, request, modelId);
            assetAdmission = assetExtractionCoordination.admit(
                context.tenantId(), projectId, scriptId, assetFingerprint, null, 1, 1);
            if (!"ACQUIRED".equals(assetAdmission.kind())) {
                AiExecutionTaskEntity owner = executionService.requireTask(assetAdmission.ownerExecutionId());
                if (context.userId().equals(owner.userId) && "REUSED".equals(assetAdmission.kind())) {
                    return responseMapper.toResponse(owner).withAdmission("REUSED", owner.id);
                }
                if (context.userId().equals(owner.userId)) {
                    return responseMapper.toResponse(owner).withAdmission("CONFLICT", owner.id);
                }
                throw new BusinessException(ErrorCode.SCRIPT_VERSION_CONFLICT, "当前剧本已有进行中的资产提取任务。");
            }
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
        if (assetAdmission != null && !assetExtractionCoordination.attach(
            context.tenantId(), projectId, scriptId, assetFingerprint,
            execution.id, execution.executionVersion, 0
        )) {
            throw new IllegalStateException("资产提取协调记录在提交期间被替换。");
        }
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

    private String assetFingerprint(
        TenantContext context, Long projectId, Long scriptId, Long scriptVersionId,
        ScopedAssetReextractionRequest request, Long modelId
    ) {
        try {
            Map<String, Object> fields = new TreeMap<>();
            fields.put("modelId", modelId);
            fields.put("promptPolicy", request.promptPolicy().trim().toUpperCase());
            fields.put("scriptVersionId", scriptVersionId);
            fields.put("scope", request.targetType().trim().toUpperCase());
            fields.put("userId", context.userId());
            fields.put("episodes", jdbc.query("""
                select stable_key, content_fingerprint from script_episode
                 where tenant_id=? and project_id=? and script_id=? and status='ACTIVE' and retired_at is null
                 order by episode_no, id
                """, (row, index) -> row.getString("stable_key") + ":" + row.getString("content_fingerprint"),
                context.tenantId(), projectId, scriptId));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                objectMapper.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成资产提取请求指纹。", exception);
        }
    }

    void updateResult(ScriptAiOperationEntity operation) {
        operationMapper.updateById(operation);
    }
}
