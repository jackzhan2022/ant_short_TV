package com.antshorttv.script;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class ScriptElementDraftService {

    private final JdbcTemplate jdbcTemplate;

    ScriptElementDraftService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void replaceDrafts(Long tenantId, Long projectId, Long userId, ScriptElementExtractionResult result) {
        switch (result.elementType()) {
            case CHARACTER -> replaceCharacters(tenantId, projectId, userId, result.characters());
            case SCENE -> replaceScenes(tenantId, projectId, userId, result.scenes());
            case PROP -> replaceProps(tenantId, projectId, userId, result.props());
            case ALL -> {
                replaceCharacters(tenantId, projectId, userId, result.characters());
                replaceScenes(tenantId, projectId, userId, result.scenes());
                replaceProps(tenantId, projectId, userId, result.props());
            }
        }
    }

    void replaceAnalysisDrafts(Long tenantId, Long projectId, Long userId, ScriptElementExtractionResult result) {
        replaceCharactersAsPendingReview(tenantId, projectId, userId, result.characters());
        replaceScenesAsPendingReview(tenantId, projectId, userId, result.scenes());
        replacePropsAsPendingReview(tenantId, projectId, userId, result.props());
    }

    private void replaceCharactersAsPendingReview(
        Long tenantId,
        Long projectId,
        Long userId,
        List<ScriptElementExtractionResult.CharacterElement> characters
    ) {
        clearUnconfirmedElements(ScriptElementType.CHARACTER.tableName(), tenantId, projectId);
        for (ScriptElementExtractionResult.CharacterElement item : characters) {
            insertCharacterAsset(tenantId, projectId, userId, item, null, "PENDING_REVIEW");
        }
    }

    private void replaceScenesAsPendingReview(
        Long tenantId,
        Long projectId,
        Long userId,
        List<ScriptElementExtractionResult.SceneElement> scenes
    ) {
        clearUnconfirmedElements(ScriptElementType.SCENE.tableName(), tenantId, projectId);
        for (ScriptElementExtractionResult.SceneElement item : scenes) {
            insertSceneAsset(tenantId, projectId, userId, item, null, "PENDING_REVIEW");
        }
    }

    private void replacePropsAsPendingReview(
        Long tenantId,
        Long projectId,
        Long userId,
        List<ScriptElementExtractionResult.PropElement> props
    ) {
        clearUnconfirmedElements(ScriptElementType.PROP.tableName(), tenantId, projectId);
        for (ScriptElementExtractionResult.PropElement item : props) {
            insertPropAsset(tenantId, projectId, userId, item, null, "PENDING_REVIEW");
        }
    }

    private void replaceCharacters(
        Long tenantId,
        Long projectId,
        Long userId,
        List<ScriptElementExtractionResult.CharacterElement> characters
    ) {
        clearUnconfirmedElements(ScriptElementType.CHARACTER.tableName(), tenantId, projectId);
        for (ScriptElementExtractionResult.CharacterElement item : characters) {
            Long mergeTargetId = confirmedElementId(ScriptElementType.CHARACTER.tableName(), tenantId, projectId, item.name());
            insertCharacterAsset(tenantId, projectId, userId, item, mergeTargetId, mergeTargetId == null ? "DRAFT" : "PENDING_REVIEW");
        }
    }

    private void replaceScenes(
        Long tenantId,
        Long projectId,
        Long userId,
        List<ScriptElementExtractionResult.SceneElement> scenes
    ) {
        clearUnconfirmedElements(ScriptElementType.SCENE.tableName(), tenantId, projectId);
        for (ScriptElementExtractionResult.SceneElement item : scenes) {
            Long mergeTargetId = confirmedElementId(ScriptElementType.SCENE.tableName(), tenantId, projectId, item.name());
            insertSceneAsset(tenantId, projectId, userId, item, mergeTargetId, mergeTargetId == null ? "DRAFT" : "PENDING_REVIEW");
        }
    }

    private void replaceProps(
        Long tenantId,
        Long projectId,
        Long userId,
        List<ScriptElementExtractionResult.PropElement> props
    ) {
        clearUnconfirmedElements(ScriptElementType.PROP.tableName(), tenantId, projectId);
        for (ScriptElementExtractionResult.PropElement item : props) {
            Long mergeTargetId = confirmedElementId(ScriptElementType.PROP.tableName(), tenantId, projectId, item.name());
            insertPropAsset(tenantId, projectId, userId, item, mergeTargetId, mergeTargetId == null ? "DRAFT" : "PENDING_REVIEW");
        }
    }

    private Long confirmedElementId(String table, Long tenantId, Long projectId, String name) {
        List<Long> ids = jdbcTemplate.query("""
            select id
              from %s
             where tenant_id = ?
               and project_id = ?
               and deleted_at is null
               and status = 'CONFIRMED'
               and name = ?
             order by id desc
             limit 1
            """.formatted(table), (rs, rowNum) -> rs.getLong("id"), tenantId, projectId, name);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void clearUnconfirmedElements(String table, Long tenantId, Long projectId) {
        jdbcTemplate.update("""
            update %s
               set deleted_at = now(),
                   updated_at = now()
             where tenant_id = ?
               and project_id = ?
               and deleted_at is null
               and status <> 'CONFIRMED'
            """.formatted(table), tenantId, projectId);
    }

    private void insertCharacterAsset(
        Long tenantId,
        Long projectId,
        Long userId,
        ScriptElementExtractionResult.CharacterElement item,
        Long mergeTargetId,
        String status
    ) {
        jdbcTemplate.update("""
            insert into character_asset
              (tenant_id, project_id, name, role_type, gender, age_range, identity, personality, appearance, relationship_text, plot_function, prompt, status, merge_target_id, created_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, null, null, ?, ?, ?, ?, now(), now())
            """,
            tenantId,
            projectId,
            blankToNull(item.name()),
            defaultValue(item.roleType(), "SUPPORTING"),
            blankToNull(item.gender()),
            blankToNull(item.ageRange()),
            blankToNull(item.identity()),
            joinTags(item.personality()),
            blankToNull(item.appearance()),
            blankToNull(item.prompt()),
            normalizeStatus(status),
            mergeTargetId,
            userId);
    }

    private void insertSceneAsset(
        Long tenantId,
        Long projectId,
        Long userId,
        ScriptElementExtractionResult.SceneElement item,
        Long mergeTargetId,
        String status
    ) {
        jdbcTemplate.update("""
            insert into scene_asset
              (tenant_id, project_id, name, scene_type, time_atmosphere, description, visual_style, plot_reference, prompt, status, merge_target_id, created_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?, now(), now())
            """,
            tenantId,
            projectId,
            blankToNull(item.name()),
            defaultValue(item.sceneType(), "INTERIOR"),
            blankToNull(item.atmosphere()),
            blankToNull(item.description()),
            blankToNull(item.visualStyle()),
            blankToNull(item.prompt()),
            normalizeStatus(status),
            mergeTargetId,
            userId);
    }

    private void insertPropAsset(
        Long tenantId,
        Long projectId,
        Long userId,
        ScriptElementExtractionResult.PropElement item,
        Long mergeTargetId,
        String status
    ) {
        jdbcTemplate.update("""
            insert into prop_asset
              (tenant_id, project_id, name, prop_type, appearance, plot_function, related_character, prompt, status, merge_target_id, created_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """,
            tenantId,
            projectId,
            blankToNull(item.name()),
            defaultValue(item.propType(), "KEY_PROP"),
            blankToNull(item.appearance()),
            blankToNull(item.plotFunction()),
            blankToNull(item.relatedCharacter()),
            blankToNull(item.prompt()),
            normalizeStatus(status),
            mergeTargetId,
            userId);
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "DRAFT" : status.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String joinTags(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
            .map(item -> item == null ? "" : item.trim())
            .filter(item -> !item.isBlank())
            .reduce((left, right) -> left + "、" + right)
            .orElse("");
        return joined.isBlank() ? null : joined;
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
