package com.antshorttv.script;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StoryboardAssetReferenceBackfillService {
    private final JdbcTemplate jdbc;
    private final StoryboardAssetReferenceRepository repository;
    private final StoryboardAssetReferenceLegacyResolver resolver;

    public StoryboardAssetReferenceBackfillService(
        JdbcTemplate jdbc,
        StoryboardAssetReferenceRepository repository,
        StoryboardAssetReferenceLegacyResolver resolver
    ) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.resolver = resolver;
    }

    public StoryboardAssetReferenceBackfillResult backfill(Integer requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit == null ? 100 : requestedLimit, 500));
        List<Map<String, Object>> candidates = jdbc.queryForList("""
            select storyboard.id,storyboard.tenant_id,storyboard.project_id,storyboard.created_by
              from storyboard storyboard
             where storyboard.deleted_at is null
               and (coalesce(trim(storyboard.characters),'') <> ''
                 or coalesce(trim(storyboard.scene),'') <> ''
                 or coalesce(trim(storyboard.props),'') <> '')
               and not exists (select 1 from storyboard_asset_reference reference
                 where reference.storyboard_id=storyboard.id and reference.tenant_id=storyboard.tenant_id
                   and reference.project_id=storyboard.project_id and reference.retired_at is null)
             order by storyboard.id limit %d
            """.formatted(limit));
        int processed = 0;
        int resolved = 0;
        int pending = 0;
        int unresolved = 0;
        int failed = 0;
        for (Map<String, Object> candidate : candidates) {
            Long storyboardId = number(candidate.get("id"));
            Long tenantId = number(candidate.get("tenant_id"));
            Long projectId = number(candidate.get("project_id"));
            try {
                LegacyStoryboardReferences legacy = resolver.resolveStoryboard(
                    tenantId, projectId, storyboardId);
                if (legacy.formal() || legacy.references().isEmpty()) continue;
                List<StoryboardAssetReferenceEntity> entities = new ArrayList<>();
                for (LegacyReference item : legacy.references()) {
                    StoryboardAssetReferenceEntity entity = new StoryboardAssetReferenceEntity();
                    entity.assetType = item.assetType();
                    entity.assetId = item.assetId();
                    entity.variantId = item.variantId();
                    entity.referenceRole = item.referenceRole();
                    entity.sortOrder = item.sortOrder();
                    entity.resolutionStatus = item.resolutionStatus();
                    entity.sourceType = "LEGACY";
                    entity.sourceName = item.sourceName();
                    entity.lockedByUser = false;
                    entity.createdBy = number(candidate.get("created_by"));
                    entities.add(entity);
                    if (item.assetId() == null) unresolved++;
                    else {
                        resolved++;
                        if ("ASSET_PENDING".equals(item.resolutionStatus())) pending++;
                    }
                }
                repository.replace(tenantId, projectId, storyboardId, entities, LocalDateTime.now());
                processed++;
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        return new StoryboardAssetReferenceBackfillResult(
            processed, resolved, pending, unresolved, candidates.size() - processed - failed, failed);
    }

    public int countConsistencyDrift() {
        int drift = 0;
        List<Map<String, Object>> storyboards = jdbc.queryForList("""
            select distinct storyboard.id,storyboard.tenant_id,storyboard.project_id,
                   storyboard.characters,storyboard.scene,storyboard.props
              from storyboard storyboard join storyboard_asset_reference reference
                on reference.storyboard_id=storyboard.id and reference.retired_at is null
             where storyboard.deleted_at is null
            """);
        for (Map<String, Object> storyboard : storyboards) {
            List<StoryboardAssetReferenceEntity> references = repository.listActive(
                number(storyboard.get("tenant_id")), number(storyboard.get("project_id")),
                number(storyboard.get("id")));
            if (!same(storyboard.get("characters"), references, "CHARACTER")
                || !same(storyboard.get("scene"), references, "SCENE")
                || !same(storyboard.get("props"), references, "PROP")) drift++;
        }
        return drift;
    }

    private boolean same(Object legacy, List<StoryboardAssetReferenceEntity> references, String type) {
        List<String> expected = StoryboardAssetReferenceLegacyResolver.split(
            legacy == null ? null : legacy.toString());
        List<String> actual = references.stream().filter(item -> type.equals(item.assetType))
            .map(item -> item.sourceName).toList();
        return expected.equals(actual);
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}

record StoryboardAssetReferenceBackfillResult(
    int processed,
    int resolved,
    int pending,
    int unresolved,
    int skipped,
    int failed
) {
}
