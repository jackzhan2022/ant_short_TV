package com.antshorttv.script;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScriptAssetNormalizationService {
    private final ScriptAssetNormalizationRunMapper runMapper;
    private final ScriptAssetCandidateMapper candidateMapper;
    private final ScriptAssetCandidateAliasMapper aliasMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ScriptAssetRecognitionNormalizer normalizer;

    public ScriptAssetNormalizationService(
        ScriptAssetNormalizationRunMapper runMapper,
        ScriptAssetCandidateMapper candidateMapper,
        ScriptAssetCandidateAliasMapper aliasMapper,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.runMapper = runMapper;
        this.candidateMapper = candidateMapper;
        this.aliasMapper = aliasMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.normalizer = new ScriptAssetRecognitionNormalizer(objectMapper);
    }

    @Transactional
    public NormalizationPersistenceResult normalizeAndPersist(
        Long tenantId,
        Long projectId,
        Long scriptId,
        Long scriptVersionId,
        Long analysisTaskId,
        Long analysisStageId,
        Long executionId,
        Long attemptId,
        Long aiCallLogId,
        String idempotencyKey,
        String rawResponse
    ) {
        return persistNormalized(tenantId, projectId, scriptId, scriptVersionId, analysisTaskId,
            analysisStageId, executionId, attemptId, aiCallLogId, idempotencyKey, rawResponse,
            normalizer.normalize(rawResponse));
    }

    @Transactional
    public NormalizationPersistenceResult normalizePartialAndPersist(
        Long tenantId,
        Long projectId,
        Long scriptId,
        Long scriptVersionId,
        Long analysisTaskId,
        Long analysisStageId,
        Long executionId,
        Long attemptId,
        Long aiCallLogId,
        String idempotencyKey,
        String rawResponse
    ) {
        return persistNormalized(tenantId, projectId, scriptId, scriptVersionId, analysisTaskId,
            analysisStageId, executionId, attemptId, aiCallLogId, idempotencyKey, rawResponse,
            normalizer.normalizePartial(rawResponse));
    }

    private NormalizationPersistenceResult persistNormalized(
        Long tenantId,
        Long projectId,
        Long scriptId,
        Long scriptVersionId,
        Long analysisTaskId,
        Long analysisStageId,
        Long executionId,
        Long attemptId,
        Long aiCallLogId,
        String idempotencyKey,
        String rawResponse,
        ScriptAssetRecognitionNormalizer.NormalizedRecognition normalized
    ) {
        jdbcTemplate.queryForList("""
            select id from script
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
             for update
            """, Long.class, tenantId, projectId, scriptId);
        ScriptAssetNormalizationRunEntity existing = runMapper.selectOne(
            new QueryWrapper<ScriptAssetNormalizationRunEntity>()
                .eq("tenant_id", tenantId)
                .eq("idempotency_key", idempotencyKey)
                .last("limit 1"));
        if (existing != null) {
            return result(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        ScriptAssetNormalizationRunEntity run = new ScriptAssetNormalizationRunEntity();
        run.setTenantId(tenantId);
        run.setProjectId(projectId);
        run.setScriptId(scriptId);
        run.setScriptVersionId(scriptVersionId);
        run.setAnalysisTaskId(analysisTaskId);
        run.setAnalysisStageId(analysisStageId);
        run.setExecutionId(executionId);
        run.setAttemptId(attemptId);
        run.setAiCallLogId(aiCallLogId);
        run.setIdempotencyKey(idempotencyKey);
        run.setSchemaVersion("v2");
        run.setStatus(normalized.valid() ? "READY_FOR_REVIEW" : "BUSINESS_FAILED");
        run.setRawResponse(rawResponse);
        run.setNormalizedJson(normalized.normalizedJson());
        if (!normalized.valid()) {
            run.setErrorCode("ASSET_NORMALIZATION_INVALID");
            run.setErrorMessage(String.join("; ", normalized.globalErrors()));
        }
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runMapper.insert(run);

        if (normalized.valid()) {
            Set<String> recognizedTypes = normalized.candidates().stream()
                .map(candidate -> candidate.assetType().name())
                .collect(Collectors.toSet());
            for (String recognizedType : recognizedTypes) {
                jdbcTemplate.update("""
                    update script_asset_candidate
                       set review_status = 'SUPERSEDED', updated_at = now()
                     where tenant_id = ? and project_id = ? and asset_type = ?
                       and review_status = 'PENDING_REVIEW' and run_id <> ?
                       and run_id in (
                           select id from script_asset_normalization_run
                            where tenant_id = ? and project_id = ? and script_id = ?
                       )
                    """, tenantId, projectId, recognizedType, run.getId(),
                    tenantId, projectId, scriptId);
            }
        }

        for (ScriptAssetRecognitionNormalizer.NormalizedCandidate normalizedCandidate : normalized.candidates()) {
            ScriptAssetCandidateEntity candidate = new ScriptAssetCandidateEntity();
            candidate.setRunId(run.getId());
            candidate.setTenantId(tenantId);
            candidate.setProjectId(projectId);
            candidate.setAssetType(normalizedCandidate.assetType().name());
            candidate.setSourceIndex(normalizedCandidate.sourceIndex());
            candidate.setSourceKey(normalizedCandidate.sourceKey());
            candidate.setName(normalizedCandidate.name());
            candidate.setNormalizedName(normalizedCandidate.normalizedName());
            candidate.setCandidateJson(json(normalizedCandidate.data()));
            candidate.setValidationStatus(normalizedCandidate.validationErrors().isEmpty() ? "VALID" : "INVALID");
            candidate.setValidationErrorsJson(json(normalizedCandidate.validationErrors()));
            candidate.setDuplicateGroupKey(normalizedCandidate.duplicateGroupKey());
            Long proposedTarget = findCanonicalTarget(
                tenantId, projectId, normalizedCandidate.assetType(), normalizedCandidate.name());
            candidate.setProposedTargetId(proposedTarget);
            candidate.setMatchType(proposedTarget == null ? "NONE" : "EXACT_NAME");
            candidate.setMatchConfidence(proposedTarget == null ? BigDecimal.ZERO : BigDecimal.ONE);
            candidate.setMatchEvidenceJson(json(normalizedCandidate.matchEvidence()));
            candidate.setReviewStatus(normalizedCandidate.reviewStatus());
            candidate.setCreatedAt(now);
            candidate.setUpdatedAt(now);
            candidateMapper.insert(candidate);
            for (String alias : normalizedCandidate.aliases()) {
                ScriptAssetCandidateAliasEntity entity = new ScriptAssetCandidateAliasEntity();
                entity.setCandidateId(candidate.getId());
                entity.setAliasName(alias);
                entity.setNormalizedAlias(normalizeName(alias));
                entity.setSource("AI_EXPLICIT");
                entity.setEvidenceJson("{\"field\":\"aliases\"}");
                entity.setCreatedAt(now);
                aliasMapper.insert(entity);
            }
        }
        return new NormalizationPersistenceResult(
            run.getId(), run.getStatus(), normalized.candidates().size(), normalized.valid());
    }

    @Transactional
    public void attachAnalysisResult(Long tenantId, Long runId, Long analysisResultId) {
        ScriptAssetNormalizationRunEntity run = runMapper.selectOne(
            new QueryWrapper<ScriptAssetNormalizationRunEntity>()
                .eq("tenant_id", tenantId)
                .eq("id", runId)
                .last("limit 1"));
        if (run == null) {
            throw new IllegalArgumentException("资产归一化运行记录不存在。");
        }
        if (run.getAnalysisResultId() != null && !run.getAnalysisResultId().equals(analysisResultId)) {
            throw new IllegalStateException("资产归一化运行记录已关联其他分析结果。");
        }
        if (run.getAnalysisResultId() == null) {
            run.setAnalysisResultId(analysisResultId);
            run.setUpdatedAt(LocalDateTime.now());
            runMapper.updateById(run);
        }
    }

    private NormalizationPersistenceResult result(ScriptAssetNormalizationRunEntity run) {
        Long count = candidateMapper.selectCount(new QueryWrapper<ScriptAssetCandidateEntity>().eq("run_id", run.getId()));
        return new NormalizationPersistenceResult(
            run.getId(), run.getStatus(), count.intValue(), "READY_FOR_REVIEW".equals(run.getStatus()));
    }

    private Long findCanonicalTarget(Long tenantId, Long projectId, AssetType type, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String table = switch (type) {
            case CHARACTER -> "character_asset";
            case SCENE -> "scene_asset";
            case PROP -> "prop_asset";
        };
        String normalized = normalizeName(name);
        List<Map<String, Object>> assets = jdbcTemplate.queryForList("""
            select id, name, status from %s
             where tenant_id = ? and project_id = ? and deleted_at is null
             order by case when status = 'CONFIRMED' then 0 else 1 end, id
            """.formatted(table), tenantId, projectId);
        for (Map<String, Object> asset : assets) {
            if (normalized.equals(normalizeName(String.valueOf(asset.get("name"))))) {
                return ((Number) asset.get("id")).longValue();
            }
        }
        List<Long> aliasTargets = jdbcTemplate.query("""
            select decision.result_asset_id
              from script_asset_candidate candidate
              join script_asset_promotion_decision decision on decision.candidate_id = candidate.id
             where candidate.tenant_id = ? and candidate.project_id = ? and candidate.asset_type = ?
               and (candidate.normalized_name = ? or exists (
                   select 1 from script_asset_candidate_alias alias
                    where alias.candidate_id = candidate.id and alias.normalized_alias = ?
               ))
               and decision.status = 'COMPLETED'
               and decision.result_asset_id is not null
             order by decision.id desc limit 1
            """, (rs, rowNum) -> rs.getLong(1), tenantId, projectId, type.name(), normalized, normalized);
        return aliasTargets.isEmpty() ? null : aliasTargets.get(0);
    }

    private String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize normalization evidence", exception);
        }
    }

    public record NormalizationPersistenceResult(long runId, String status, int candidateCount, boolean valid) {}
}
