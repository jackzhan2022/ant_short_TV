package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScriptAssetCandidateReviewService {
    private final ScriptAssetCandidateMapper candidateMapper;
    private final ScriptAssetCandidateAliasMapper aliasMapper;
    private final ScriptAssetNormalizationRunMapper runMapper;
    private final ScriptAssetPromotionDecisionMapper decisionMapper;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ScriptAssetCandidateReviewService(
        ScriptAssetCandidateMapper candidateMapper,
        ScriptAssetCandidateAliasMapper aliasMapper,
        ScriptAssetNormalizationRunMapper runMapper,
        ScriptAssetPromotionDecisionMapper decisionMapper,
        JdbcTemplate jdbc,
        ObjectMapper objectMapper
    ) {
        this.candidateMapper = candidateMapper;
        this.aliasMapper = aliasMapper;
        this.runMapper = runMapper;
        this.decisionMapper = decisionMapper;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<CandidateResponse> list(Long tenantId, Long projectId, String reviewStatus, String assetType) {
        return listPage(tenantId, projectId, reviewStatus, assetType, 1, 500).items();
    }

    public CandidatePage listPage(
        Long tenantId, Long projectId, String reviewStatus, String assetType, Integer page, Integer pageSize
    ) {
        int safePage = page == null ? 1 : Math.max(1, page);
        int safePageSize = pageSize == null ? 20 : Math.max(1, Math.min(100, pageSize));
        QueryWrapper<ScriptAssetCandidateEntity> countQuery = candidateQuery(
            tenantId, projectId, reviewStatus, assetType);
        long total = candidateMapper.selectCount(countQuery);
        QueryWrapper<ScriptAssetCandidateEntity> query = candidateQuery(
            tenantId, projectId, reviewStatus, assetType)
            .orderByDesc("id")
            .last("limit " + safePageSize + " offset " + ((safePage - 1) * safePageSize));
        List<CandidateResponse> items = candidateMapper.selectList(query).stream()
            .map(this::response).toList();
        return new CandidatePage(items, total, safePage, safePageSize);
    }

    private QueryWrapper<ScriptAssetCandidateEntity> candidateQuery(
        Long tenantId, Long projectId, String reviewStatus, String assetType
    ) {
        QueryWrapper<ScriptAssetCandidateEntity> query = new QueryWrapper<ScriptAssetCandidateEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId);
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            query.eq("review_status", reviewStatus.trim().toUpperCase());
        }
        if (assetType != null && !assetType.isBlank()) {
            query.eq("asset_type", AssetType.fromStorageValue(assetType).name());
        }
        return query;
    }

    public CandidateResponse detail(Long tenantId, Long projectId, Long candidateId) {
        ScriptAssetCandidateEntity candidate = ownedCandidate(tenantId, projectId, candidateId, false);
        return response(candidate);
    }

    @Transactional
    public DecisionResponse decide(
        Long tenantId,
        Long projectId,
        Long candidateId,
        Long decidedBy,
        DecisionCommand command
    ) {
        if (command == null || command.decisionType() == null || command.idempotencyKey() == null
            || command.idempotencyKey().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "决策类型和幂等键不能为空。");
        }
        ScriptAssetPromotionDecisionEntity existing = decisionMapper.selectOne(
            new QueryWrapper<ScriptAssetPromotionDecisionEntity>()
                .eq("tenant_id", tenantId)
                .eq("idempotency_key", command.idempotencyKey().trim())
                .last("limit 1"));
        if (existing != null) {
            return replay(existing, projectId, candidateId, command);
        }

        jdbc.queryForObject(
            "select id from script_asset_candidate where tenant_id = ? and project_id = ? and id = ? for update",
            Long.class, tenantId, projectId, candidateId);
        existing = decisionMapper.selectOne(new QueryWrapper<ScriptAssetPromotionDecisionEntity>()
            .eq("tenant_id", tenantId).eq("idempotency_key", command.idempotencyKey().trim())
            .last("limit 1"));
        if (existing != null) {
            return replay(existing, projectId, candidateId, command);
        }
        ScriptAssetCandidateEntity candidate = ownedCandidate(tenantId, projectId, candidateId, false);
        ScriptAssetNormalizationRunEntity run = runMapper.selectById(candidate.getRunId());
        if (run == null || !tenantId.equals(run.getTenantId()) || !projectId.equals(run.getProjectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "归一化运行记录不存在。");
        }
        Integer current = jdbc.queryForObject("""
            select count(*) from script
             where id = ? and tenant_id = ? and project_id = ? and deleted_at is null
               and current_version_id = ?
            """, Integer.class, run.getScriptId(), tenantId, projectId, run.getScriptVersionId());
        if (current == null || current == 0) {
            throw new BusinessException(ErrorCode.SCRIPT_VERSION_CONFLICT, "候选来自旧剧本版本，请重新识别后再处理。");
        }

        String decisionType = command.decisionType().trim().toUpperCase();
        Long resultAssetId = null;
        switch (decisionType) {
            case "ACCEPT_NEW" -> {
                requireReviewable(candidate);
                resultAssetId = insertCanonical(candidate, decidedBy);
                candidate.setReviewStatus("ACCEPTED_NEW");
            }
            case "ACCEPT_MERGE" -> {
                requireReviewable(candidate);
                Long targetId = command.targetAssetId() == null
                    ? candidate.getProposedTargetId() : command.targetAssetId();
                requireOwnedTarget(tenantId, projectId, candidate.getAssetType(), targetId);
                mergeCanonical(candidate, targetId);
                resultAssetId = targetId;
                candidate.setProposedTargetId(targetId);
                candidate.setReviewStatus("ACCEPTED_MERGE");
            }
            case "RETARGET" -> {
                requireReviewable(candidate);
                requireOwnedTarget(tenantId, projectId, candidate.getAssetType(), command.targetAssetId());
                candidate.setProposedTargetId(command.targetAssetId());
                candidate.setMatchType("USER_SELECTED");
                candidate.setReviewStatus("PENDING_REVIEW");
            }
            case "REJECT" -> {
                if (!"PENDING_REVIEW".equals(candidate.getReviewStatus())) {
                    throw new BusinessException(ErrorCode.AI_EXECUTION_STATUS_INVALID, "候选已完成审核。");
                }
                candidate.setReviewStatus("REJECTED");
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的候选决策类型。");
        }
        candidate.setUpdatedAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);

        ScriptAssetPromotionDecisionEntity decision = new ScriptAssetPromotionDecisionEntity();
        decision.setTenantId(tenantId);
        decision.setProjectId(projectId);
        decision.setCandidateId(candidateId);
        decision.setDecisionType(decisionType);
        decision.setRequestedTargetId(command.targetAssetId());
        decision.setResultAssetId(resultAssetId);
        decision.setIdempotencyKey(command.idempotencyKey().trim());
        decision.setStatus("COMPLETED");
        decision.setDecidedBy(decidedBy);
        decision.setCreatedAt(LocalDateTime.now());
        decision.setUpdatedAt(LocalDateTime.now());
        decisionMapper.insert(decision);
        return decisionResponse(decision);
    }

    private DecisionResponse replay(
        ScriptAssetPromotionDecisionEntity existing,
        Long projectId,
        Long candidateId,
        DecisionCommand command
    ) {
        String requestedType = command.decisionType().trim().toUpperCase();
        if (!projectId.equals(existing.getProjectId()) || !candidateId.equals(existing.getCandidateId())
            || !requestedType.equals(existing.getDecisionType())
            || !java.util.Objects.equals(command.targetAssetId(), existing.getRequestedTargetId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "幂等键已用于其他候选决策。");
        }
        return decisionResponse(existing);
    }

    private ScriptAssetCandidateEntity ownedCandidate(
        Long tenantId, Long projectId, Long candidateId, boolean ignored
    ) {
        ScriptAssetCandidateEntity candidate = candidateMapper.selectOne(
            new QueryWrapper<ScriptAssetCandidateEntity>()
                .eq("tenant_id", tenantId)
                .eq("project_id", projectId)
                .eq("id", candidateId)
                .last("limit 1"));
        if (candidate == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资产候选不存在。");
        }
        return candidate;
    }

    private void requireReviewable(ScriptAssetCandidateEntity candidate) {
        if (!"VALID".equals(candidate.getValidationStatus()) || candidate.getName() == null
            || candidate.getName().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无效候选不能进入正式资产库。");
        }
        if (!"PENDING_REVIEW".equals(candidate.getReviewStatus())) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_STATUS_INVALID, "候选已完成审核。");
        }
    }

    private Long insertCanonical(ScriptAssetCandidateEntity candidate, Long userId) {
        Map<String, Object> data = candidateData(candidate);
        String sql;
        List<Object> values;
        switch (AssetType.fromStorageValue(candidate.getAssetType())) {
            case CHARACTER -> {
                sql = """
                    insert into character_asset
                      (tenant_id, project_id, name, role_type, gender, age_range, identity, personality,
                       appearance, prompt, status, merge_target_id, created_by, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', null, ?, now(), now())
                    """;
                values = java.util.Arrays.asList(candidate.getTenantId(), candidate.getProjectId(), candidate.getName(),
                    value(data, "roleType", "SUPPORTING"), nullable(data, "gender"), nullable(data, "ageRange"),
                    nullable(data, "identity"), personality(data), nullable(data, "appearance"),
                    nullable(data, "prompt"), userId);
            }
            case SCENE -> {
                sql = """
                    insert into scene_asset
                      (tenant_id, project_id, name, scene_type, time_atmosphere, description, visual_style,
                       prompt, status, merge_target_id, created_by, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', null, ?, now(), now())
                    """;
                values = java.util.Arrays.asList(candidate.getTenantId(), candidate.getProjectId(), candidate.getName(),
                    value(data, "sceneType", "INTERIOR"), nullable(data, "atmosphere"),
                    nullable(data, "description"), nullable(data, "visualStyle"), nullable(data, "prompt"), userId);
            }
            case PROP -> {
                sql = """
                    insert into prop_asset
                      (tenant_id, project_id, name, prop_type, appearance, plot_function, related_character,
                       prompt, status, merge_target_id, created_by, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', null, ?, now(), now())
                    """;
                values = java.util.Arrays.asList(candidate.getTenantId(), candidate.getProjectId(), candidate.getName(),
                    value(data, "propType", "KEY_PROP"), nullable(data, "appearance"),
                    nullable(data, "plotFunction"), nullable(data, "relatedCharacter"),
                    nullable(data, "prompt"), userId);
            }
            default -> throw new IllegalStateException("Unsupported asset type");
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.size(); index++) {
                statement.setObject(index + 1, values.get(index));
            }
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private void mergeCanonical(ScriptAssetCandidateEntity candidate, Long targetId) {
        Map<String, Object> data = candidateData(candidate);
        int updated = switch (AssetType.fromStorageValue(candidate.getAssetType())) {
            case CHARACTER -> jdbc.update("""
                update character_asset set role_type = coalesce(?, role_type), gender = coalesce(?, gender), age_range = coalesce(?, age_range),
                    identity = coalesce(?, identity), personality = coalesce(?, personality),
                    appearance = coalesce(?, appearance), prompt = coalesce(?, prompt), updated_at = now()
                 where id = ? and tenant_id = ? and project_id = ? and status = 'CONFIRMED' and deleted_at is null
                """, nonDefault(data, "roleType", "SUPPORTING"), nullable(data, "gender"), nullable(data, "ageRange"),
                nullable(data, "identity"), personality(data), nullable(data, "appearance"), nullable(data, "prompt"),
                targetId, candidate.getTenantId(), candidate.getProjectId());
            case SCENE -> jdbc.update("""
                update scene_asset set scene_type = coalesce(?, scene_type), time_atmosphere = coalesce(?, time_atmosphere),
                    description = coalesce(?, description), visual_style = coalesce(?, visual_style),
                    prompt = coalesce(?, prompt), updated_at = now()
                 where id = ? and tenant_id = ? and project_id = ? and status = 'CONFIRMED' and deleted_at is null
                """, nonDefault(data, "sceneType", "INTERIOR"), nullable(data, "atmosphere"), nullable(data, "description"),
                nullable(data, "visualStyle"), nullable(data, "prompt"), targetId,
                candidate.getTenantId(), candidate.getProjectId());
            case PROP -> jdbc.update("""
                update prop_asset set prop_type = coalesce(?, prop_type), appearance = coalesce(?, appearance),
                    plot_function = coalesce(?, plot_function), related_character = coalesce(?, related_character),
                    prompt = coalesce(?, prompt), updated_at = now()
                 where id = ? and tenant_id = ? and project_id = ? and status = 'CONFIRMED' and deleted_at is null
                """, nonDefault(data, "propType", "KEY_PROP"), nullable(data, "appearance"), nullable(data, "plotFunction"),
                nullable(data, "relatedCharacter"), nullable(data, "prompt"), targetId,
                candidate.getTenantId(), candidate.getProjectId());
        };
        if (updated != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "合并目标不存在或尚未确认。");
        }
    }

    private void requireOwnedTarget(Long tenantId, Long projectId, String assetType, Long targetId) {
        if (targetId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择合并目标。");
        }
        String table = switch (AssetType.fromStorageValue(assetType)) {
            case CHARACTER -> "character_asset";
            case SCENE -> "scene_asset";
            case PROP -> "prop_asset";
        };
        Integer count = jdbc.queryForObject("select count(*) from " + table
            + " where id = ? and tenant_id = ? and project_id = ? and status = 'CONFIRMED' and deleted_at is null",
            Integer.class, targetId, tenantId, projectId);
        if (count == null || count != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "合并目标不存在或不属于当前项目。");
        }
    }

    private CandidateResponse response(ScriptAssetCandidateEntity candidate) {
        List<AliasResponse> aliases = aliasMapper.selectList(
            new QueryWrapper<ScriptAssetCandidateAliasEntity>()
                .eq("candidate_id", candidate.getId()).orderByAsc("id"))
            .stream().map(alias -> new AliasResponse(alias.getAliasName(), alias.getNormalizedAlias(),
                alias.getSource(), alias.getEvidenceJson())).toList();
        return new CandidateResponse(candidate.getId(), candidate.getRunId(), candidate.getAssetType(),
            candidate.getSourceIndex(), candidate.getSourceKey(), candidate.getName(), candidate.getNormalizedName(),
            candidate.getCandidateJson(), candidate.getValidationStatus(), candidate.getValidationErrorsJson(),
            candidate.getDuplicateGroupKey(), candidate.getProposedTargetId(), candidate.getMatchType(),
            candidate.getMatchConfidence(), candidate.getMatchEvidenceJson(), candidate.getReviewStatus(), aliases);
    }

    private DecisionResponse decisionResponse(ScriptAssetPromotionDecisionEntity decision) {
        return new DecisionResponse(decision.getId(), decision.getCandidateId(), decision.getDecisionType(),
            decision.getRequestedTargetId(), decision.getResultAssetId(), decision.getStatus(),
            decision.getIdempotencyKey());
    }

    private Map<String, Object> candidateData(ScriptAssetCandidateEntity candidate) {
        try {
            return objectMapper.readValue(candidate.getCandidateJson(), new TypeReference<>() {});
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "候选数据无法解析。");
        }
    }

    private String value(Map<String, Object> data, String key, String fallback) {
        String value = nullable(data, key);
        return value == null ? fallback : value;
    }

    private String nonDefault(Map<String, Object> data, String key, String defaultValue) {
        String value = value(data, key, defaultValue);
        return defaultValue.equals(value) ? null : value;
    }

    private String nullable(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String personality(Map<String, Object> data) {
        Object value = data.get("personality");
        if (value instanceof List<?> list) {
            String joined = list.stream().map(String::valueOf).filter(item -> !item.isBlank())
                .reduce((left, right) -> left + "," + right).orElse("");
            return joined.isBlank() ? null : joined;
        }
        return nullable(data, "personality");
    }

    public record DecisionCommand(String decisionType, Long targetAssetId, String idempotencyKey) {}
    public record CandidatePage(List<CandidateResponse> items, long total, int page, int pageSize) {}
    public record AliasResponse(String name, String normalizedName, String source, String evidenceJson) {}
    public record CandidateResponse(
        Long id, Long runId, String assetType, Integer sourceIndex, String sourceKey, String name,
        String normalizedName, String candidateJson, String validationStatus, String validationErrorsJson,
        String duplicateGroupKey, Long proposedTargetId, String matchType,
        java.math.BigDecimal matchConfidence, String matchEvidenceJson, String reviewStatus,
        List<AliasResponse> aliases
    ) {}
    public record DecisionResponse(
        Long id, Long candidateId, String decisionType, Long requestedTargetId, Long resultAssetId,
        String status, String idempotencyKey
    ) {}
}
