package com.antshorttv.aiimage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.script.AssetVisualVariantService;
import com.antshorttv.security.TenantContext;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetImageBatchService {
    private static final Set<String> ASSET_TYPES = Set.of("CHARACTER", "SCENE", "PROP");
    private static final Set<String> MODES = Set.of("PRIMARY", "ALL");
    private static final Set<String> ASPECT_RATIOS = Set.of("1:1", "3:4", "4:3", "9:16", "16:9");

    private final ProjectPermissionGuard permissionGuard;
    private final JdbcTemplate jdbc;
    private final AssetVisualVariantService variantService;
    private final AiImageTaskService imageTaskService;

    AssetImageBatchService(
        ProjectPermissionGuard permissionGuard,
        JdbcTemplate jdbc,
        AssetVisualVariantService variantService,
        AiImageTaskService imageTaskService
    ) {
        this.permissionGuard = permissionGuard;
        this.jdbc = jdbc;
        this.variantService = variantService;
        this.imageTaskService = imageTaskService;
    }

    AssetImageBatchPreflightResponse preflight(
        Long tenantId, Long projectId, AssetImageBatchRequest request
    ) {
        permissionGuard.require(tenantId, projectId, "ELEMENT:VIEW");
        BatchPlan plan = plan(tenantId, projectId, request);
        return preflightResponse(request, plan);
    }

    @Transactional
    AssetImageBatchResponse create(
        Long tenantId, Long projectId, AssetImageBatchRequest request, String requestedIdempotencyKey
    ) {
        TenantContext context = permissionGuard.require(tenantId, projectId, "AI_IMAGE_TASK:CREATE");
        String idempotencyKey = valueOrUuid(requestedIdempotencyKey);
        List<Long> existing = jdbc.queryForList("""
            select id from asset_image_batch
             where tenant_id = ? and project_id = ? and idempotency_key = ?
             limit 1
            """, Long.class, tenantId, projectId, idempotencyKey);
        if (!existing.isEmpty()) return getInternal(tenantId, projectId, existing.get(0));

        BatchPlan plan = plan(tenantId, projectId, request);
        if (plan.plannedTasks() == 0) {
            throw invalid("所选资产没有可生成的视觉形象。");
        }
        LocalDateTime now = LocalDateTime.now();
        String creationToken = UUID.randomUUID().toString();
        BatchInsert batchInsert = insertBatch(
            tenantId, projectId, request, idempotencyKey, creationToken, context.userId(), now);
        Long batchId = batchInsert.batchId();
        if (!batchInsert.owner()) return getInternal(tenantId, projectId, batchId);

        List<PersistedCandidate> persisted = new ArrayList<>();
        for (Candidate candidate : plan.candidates()) {
            Candidate actual = candidate;
            if (candidate.createDefault()) {
                AssetVisualVariantService.VariantResponse created = variantService.create(
                    tenantId, projectId, request.assetType(), candidate.assetId(), context.userId(),
                    new AssetVisualVariantService.VariantCommand(
                        "默认形象", null, candidate.prompt(), "BATCH_DEFAULT", "NOT_STARTED",
                        null, null, true));
                actual = candidate.withVariant(created.id(), created.name());
            }
            Long itemId = insertItem(batchId, tenantId, projectId, actual, now);
            persisted.add(new PersistedCandidate(itemId, actual));
        }

        Map<Long, Long> primaryItems = new HashMap<>();
        for (PersistedCandidate item : persisted) {
            if (item.candidate().primary()) primaryItems.put(item.candidate().assetId(), item.itemId());
        }
        for (PersistedCandidate item : persisted) {
            if ("WAITING_DEPENDENCY".equals(persistedStatus(item.candidate().classification()))) {
                Long dependencyId = primaryItems.get(item.candidate().assetId());
                if (dependencyId == null) {
                    jdbc.update("""
                        update asset_image_batch_item
                           set status = 'SKIPPED', error_message = '主形象不在本次批次中', updated_at = now()
                         where id = ?
                        """, item.itemId());
                } else {
                    jdbc.update("""
                        update asset_image_batch_item
                           set dependency_item_id = ?, updated_at = now()
                         where id = ?
                        """, dependencyId, item.itemId());
                }
            }
        }
        return getInternal(tenantId, projectId, batchId);
    }

    AssetImageBatchResponse get(Long tenantId, Long projectId, Long batchId) {
        permissionGuard.require(tenantId, projectId, "AI_IMAGE_TASK:VIEW");
        return getInternal(tenantId, projectId, batchId);
    }

    AssetImageBatchResponse getCreated(Long tenantId, Long projectId, Long batchId) {
        return getInternal(tenantId, projectId, batchId);
    }

    private BatchPlan plan(Long tenantId, Long projectId, AssetImageBatchRequest request) {
        validate(request);
        imageTaskService.validateBatchModel(tenantId, projectId, request.modelId());
        List<Long> requestedIds = request.assetIds();
        String table = assetTable(request.assetType());
        List<AssetRef> assets = jdbc.query("""
            select id, name, prompt from %s
             where tenant_id = ? and project_id = ? and deleted_at is null
               and id in (%s)
             order by id
            """.formatted(table, placeholders(requestedIds.size())),
            (rs, rowNum) -> new AssetRef(rs.getLong("id"), rs.getString("name"), rs.getString("prompt")),
            queryArguments(tenantId, projectId, requestedIds));
        if (assets.size() != requestedIds.size()) {
            throw invalid("只能选择当前项目中的有效资产。");
        }

        Map<Long, AssetRef> assetsById = new LinkedHashMap<>();
        assets.forEach(asset -> assetsById.put(asset.id(), asset));
        List<VariantRef> variants = jdbc.query("""
            select id, asset_id, name, prompt, generation_status, is_primary,
                   current_image_result_id, current_image_url
              from asset_visual_variant
             where tenant_id = ? and project_id = ? and asset_type = ? and deleted_at is null
               and asset_id in (%s)
             order by asset_id, is_primary desc, id
            """.formatted(placeholders(requestedIds.size())),
            (rs, rowNum) -> new VariantRef(
                rs.getLong("id"), rs.getLong("asset_id"), rs.getString("name"),
                rs.getString("prompt"), rs.getString("generation_status"),
                rs.getBoolean("is_primary"), rs.getObject("current_image_result_id", Long.class),
                rs.getString("current_image_url")),
            queryArguments(tenantId, projectId, request.assetType(), requestedIds));

        Map<Long, List<VariantRef>> byAsset = new LinkedHashMap<>();
        variants.forEach(variant -> byAsset.computeIfAbsent(variant.assetId(), ignored -> new ArrayList<>()).add(variant));
        List<Candidate> candidates = new ArrayList<>();
        for (AssetRef asset : assets) {
            List<VariantRef> assetVariants = new ArrayList<>(byAsset.getOrDefault(asset.id(), List.of()));
            boolean hasPrimary = assetVariants.stream().anyMatch(VariantRef::primary);
            if (!hasPrimary) {
                assetVariants.add(0, new VariantRef(
                    null, asset.id(), "默认形象", null, "NOT_STARTED", true, null, null));
            }
            boolean primaryUsable = assetVariants.stream().anyMatch(variant ->
                variant.primary() && "COMPLETED".equals(variant.status())
                    && notBlank(variant.imageUrl()));
            List<Candidate> assetCandidates = new ArrayList<>();
            for (VariantRef variant : assetVariants) {
                if ("PRIMARY".equals(request.mode()) && !variant.primary()) continue;
                String prompt = variant.primary() ? asset.prompt() : variant.prompt();
                String classification = classify(
                    variant.status(), prompt, variant.primary(), request.assetType(), primaryUsable);
                assetCandidates.add(new Candidate(
                    request.assetType(), asset.id(), variant.id(), variant.name(), prompt, variant.primary(),
                    variant.id() == null, classification));
            }
            String primaryClassification = assetCandidates.stream()
                .filter(Candidate::primary)
                .map(Candidate::classification)
                .findFirst()
                .orElse("SKIPPED_PRIMARY_UNAVAILABLE");
            for (Candidate candidate : assetCandidates) {
                candidates.add(candidate.withClassification(
                    resolveDependencyClassification(
                        candidate.classification(), primaryClassification)));
            }
        }
        return new BatchPlan(assets.size(), List.copyOf(candidates));
    }

    static String classify(
        String generationStatus, String prompt, boolean primary, String assetType, boolean primaryUsable
    ) {
        if (!notBlank(prompt)) return "SKIPPED_MISSING_PROMPT";
        if ("COMPLETED".equals(generationStatus)) return "SKIPPED_COMPLETED";
        if ("GENERATING".equals(generationStatus)) return "SKIPPED_GENERATING";
        if (!Set.of("NOT_STARTED", "FAILED").contains(generationStatus)) {
            return "SKIPPED_OTHER";
        }
        if ("CHARACTER".equals(assetType) && !primary && !primaryUsable) {
            return "WAITING_DEPENDENCY";
        }
        return "PENDING";
    }

    static String batchStatus(int pending, int running, int succeeded, int failed, int skipped) {
        if (running > 0) return "RUNNING";
        if (pending > 0) return "PENDING";
        if (failed > 0 && succeeded == 0) return "FAILED";
        if (failed > 0) return "COMPLETED_WITH_FAILURES";
        return "SUCCEEDED";
    }

    static String resolveDependencyClassification(
        String classification, String primaryClassification
    ) {
        return "WAITING_DEPENDENCY".equals(classification)
            && !"PENDING".equals(primaryClassification)
            ? "SKIPPED_PRIMARY_UNAVAILABLE"
            : classification;
    }

    private AssetImageBatchPreflightResponse preflightResponse(
        AssetImageBatchRequest request, BatchPlan plan
    ) {
        int planned = plan.plannedTasks();
        return new AssetImageBatchPreflightResponse(
            request.assetType(), request.mode(), plan.selectedAssets(), planned,
            plan.count("WAITING_DEPENDENCY"), plan.count("SKIPPED_COMPLETED"),
            plan.count("SKIPPED_GENERATING"), plan.count("SKIPPED_MISSING_PROMPT"),
            plan.count("SKIPPED_PRIMARY_UNAVAILABLE") + plan.count("SKIPPED_OTHER"),
            planned * request.imageCount());
    }

    private BatchInsert insertBatch(
        Long tenantId, Long projectId, AssetImageBatchRequest request,
        String idempotencyKey, String creationToken, Long userId, LocalDateTime now
    ) {
        jdbc.update("""
            insert into asset_image_batch
              (tenant_id, project_id, asset_type, generation_mode, model_id, aspect_ratio,
               image_count, status, idempotency_key, creation_token, created_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)
            on duplicate key update id = id
            """, tenantId, projectId, request.assetType(), request.mode(), request.modelId(),
            request.aspectRatio(), request.imageCount(), idempotencyKey, creationToken, userId, now, now);
        Map<String, Object> row = jdbc.queryForMap("""
            select id, creation_token from asset_image_batch
             where tenant_id = ? and project_id = ? and idempotency_key = ?
            """, tenantId, projectId, idempotencyKey);
        return new BatchInsert(
            number(row.get("id")), creationToken.equals(text(row.get("creation_token"))));
    }

    private Long insertItem(
        Long batchId, Long tenantId, Long projectId, Candidate candidate, LocalDateTime now
    ) {
        String status = persistedStatus(candidate.classification());
        String error = skippedMessage(candidate.classification());
        String stage = candidate.primary() ? "PRIMARY"
            : "WAITING_DEPENDENCY".equals(status) ? "DEPENDENT" : "DIRECT";
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into asset_image_batch_item
                  (batch_id, tenant_id, project_id, asset_type, asset_id, variant_id,
                   variant_name, stage, status, error_message, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, batchId);
            statement.setObject(2, tenantId);
            statement.setObject(3, projectId);
            statement.setString(4, candidate.assetType());
            statement.setObject(5, candidate.assetId());
            statement.setObject(6, candidate.variantId());
            statement.setString(7, candidate.variantName());
            statement.setString(8, stage);
            statement.setString(9, status);
            statement.setString(10, error);
            statement.setObject(11, now);
            statement.setObject(12, now);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private AssetImageBatchResponse getInternal(Long tenantId, Long projectId, Long batchId) {
        List<Map<String, Object>> batches = jdbc.queryForList("""
            select * from asset_image_batch
             where id = ? and tenant_id = ? and project_id = ?
            """, batchId, tenantId, projectId);
        if (batches.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "资产图批次不存在。");
        Map<String, Object> batch = batches.get(0);
        List<AssetImageBatchItemResponse> items = jdbc.query("""
            select id, asset_id, variant_id, variant_name, stage, status,
                   dependency_item_id, task_id, error_message
              from asset_image_batch_item
             where batch_id = ? and tenant_id = ? and project_id = ? order by id
            """, (rs, rowNum) -> new AssetImageBatchItemResponse(
                rs.getLong("id"), rs.getLong("asset_id"), rs.getLong("variant_id"),
                rs.getString("variant_name"), rs.getString("stage"), rs.getString("status"),
                rs.getObject("dependency_item_id", Long.class), rs.getObject("task_id", Long.class),
                rs.getString("error_message")), batchId, tenantId, projectId);
        int pending = count(items, "PENDING") + count(items, "WAITING_DEPENDENCY");
        int running = count(items, "RUNNING") + count(items, "DISPATCHING");
        int succeeded = count(items, "SUCCEEDED");
        int failed = count(items, "FAILED");
        int skipped = count(items, "SKIPPED");
        String status = batchStatus(pending, running, succeeded, failed, skipped);
        return new AssetImageBatchResponse(
            batchId, projectId, text(batch.get("asset_type")), text(batch.get("generation_mode")),
            status, items.size(), pending, running, succeeded, failed, skipped,
            number(batch.get("model_id")), text(batch.get("aspect_ratio")),
            ((Number) batch.get("image_count")).intValue(), items,
            localDateTime(batch.get("created_at")));
    }

    private void validate(AssetImageBatchRequest request) {
        if (request == null || !ASSET_TYPES.contains(request.assetType())) throw invalid("请选择资产类型。");
        if (!MODES.contains(request.mode())) throw invalid("请选择资产图生成范围。");
        if (request.assetIds() == null || request.assetIds().isEmpty()) throw invalid("请至少选择一个资产。");
        if (new HashSet<>(request.assetIds()).size() != request.assetIds().size()) throw invalid("资产不能重复选择。");
        if (!ASPECT_RATIOS.contains(request.aspectRatio())) throw invalid("请选择图片比例。");
        if (request.imageCount() == null || !Set.of(1, 2, 4).contains(request.imageCount())) {
            throw invalid("每个形象只能生成 1、2 或 4 张图片。");
        }
    }

    private static String assetTable(String assetType) {
        return switch (assetType) {
            case "CHARACTER" -> "character_asset";
            case "SCENE" -> "scene_asset";
            case "PROP" -> "prop_asset";
            default -> throw invalid("请选择资产类型。");
        };
    }

    private static String persistedStatus(String classification) {
        return classification.startsWith("SKIPPED_") ? "SKIPPED" : classification;
    }

    private static String skippedMessage(String classification) {
        return switch (classification) {
            case "SKIPPED_COMPLETED" -> "已生成完成";
            case "SKIPPED_GENERATING" -> "正在生成";
            case "SKIPPED_MISSING_PROMPT" -> "缺少生成提示词";
            case "SKIPPED_PRIMARY_UNAVAILABLE" -> "主形象无法生成";
            case "SKIPPED_OTHER" -> "当前状态不可生成";
            default -> null;
        };
    }

    private static int count(List<AssetImageBatchItemResponse> items, String status) {
        return (int) items.stream().filter(item -> status.equals(item.status())).count();
    }

    private static Object[] queryArguments(Long tenantId, Long projectId, List<Long> ids) {
        List<Object> values = new ArrayList<>(List.of(tenantId, projectId));
        values.addAll(ids);
        return values.toArray();
    }

    private static Object[] queryArguments(Long tenantId, Long projectId, String type, List<Long> ids) {
        List<Object> values = new ArrayList<>(List.of(tenantId, projectId, type));
        values.addAll(ids);
        return values.toArray();
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return null;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private record AssetRef(Long id, String name, String prompt) {
    }

    private record VariantRef(
        Long id, Long assetId, String name, String prompt, String status,
        boolean primary, Long resultId, String imageUrl
    ) {
    }

    private record Candidate(
        String assetType, Long assetId, Long variantId, String variantName, String prompt,
        boolean primary, boolean createDefault, String classification
    ) {
        Candidate withVariant(Long value, String name) {
            return new Candidate(assetType, assetId, value, name, prompt, primary, false, classification);
        }

        Candidate withClassification(String value) {
            return new Candidate(assetType, assetId, variantId, variantName, prompt, primary, createDefault, value);
        }
    }

    private record PersistedCandidate(Long itemId, Candidate candidate) {
    }

    private record BatchInsert(Long batchId, boolean owner) {
    }

    private record BatchPlan(int selectedAssets, List<Candidate> candidates) {
        int count(String classification) {
            return (int) candidates.stream().filter(item -> classification.equals(item.classification())).count();
        }

        int plannedTasks() {
            return count("PENDING") + count("WAITING_DEPENDENCY");
        }
    }
}
