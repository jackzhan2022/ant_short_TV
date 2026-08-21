package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class ScriptElementConfirmationService {

    private final JdbcTemplate jdbcTemplate;

    ScriptElementConfirmationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void confirm(Long tenantId, Long projectId, ScriptElementType elementType, Long elementId) {
        switch (elementType) {
            case CHARACTER -> confirmCharacterElement(tenantId, projectId, elementId);
            case SCENE -> confirmSceneElement(tenantId, projectId, elementId);
            case PROP -> confirmPropElement(tenantId, projectId, elementId);
            case ALL -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择元素类型。");
        }
    }

    private void confirmCharacterElement(Long tenantId, Long projectId, Long elementId) {
        Map<String, Object> element = jdbcTemplate.queryForMap("""
            select id, name, role_type, gender, age_range, identity, personality, appearance, prompt, status, merge_target_id
              from character_asset
             where tenant_id = ?
               and project_id = ?
               and id = ?
               and deleted_at is null
            """, tenantId, projectId, elementId);
        Long mergeTargetId = longValue(element.get("merge_target_id"));
        if ("PENDING_REVIEW".equals(stringValue(element.get("status"))) && mergeTargetId != null) {
            int updated = jdbcTemplate.update("""
                update character_asset
                   set name = ?, role_type = ?, gender = ?, age_range = ?, identity = ?, personality = ?, appearance = ?, prompt = ?, status = 'CONFIRMED', updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and status = 'CONFIRMED' and deleted_at is null
                """,
                stringValue(element.get("name")),
                stringValue(element.get("role_type")),
                stringValue(element.get("gender")),
                stringValue(element.get("age_range")),
                stringValue(element.get("identity")),
                stringValue(element.get("personality")),
                stringValue(element.get("appearance")),
                stringValue(element.get("prompt")),
                tenantId,
                projectId,
                mergeTargetId);
            requireMergeTargetUpdated(updated);
            softDelete("character_asset", tenantId, projectId, elementId);
            return;
        }
        jdbcTemplate.update("""
            update character_asset
               set status = 'CONFIRMED', merge_target_id = null, updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, tenantId, projectId, elementId);
    }

    private void confirmSceneElement(Long tenantId, Long projectId, Long elementId) {
        Map<String, Object> element = jdbcTemplate.queryForMap("""
            select id, name, scene_type, time_atmosphere, description, visual_style, prompt, status, merge_target_id
              from scene_asset
             where tenant_id = ?
               and project_id = ?
               and id = ?
               and deleted_at is null
            """, tenantId, projectId, elementId);
        Long mergeTargetId = longValue(element.get("merge_target_id"));
        if ("PENDING_REVIEW".equals(stringValue(element.get("status"))) && mergeTargetId != null) {
            int updated = jdbcTemplate.update("""
                update scene_asset
                   set name = ?, scene_type = ?, time_atmosphere = ?, description = ?, visual_style = ?, prompt = ?, status = 'CONFIRMED', updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and status = 'CONFIRMED' and deleted_at is null
                """,
                stringValue(element.get("name")),
                stringValue(element.get("scene_type")),
                stringValue(element.get("time_atmosphere")),
                stringValue(element.get("description")),
                stringValue(element.get("visual_style")),
                stringValue(element.get("prompt")),
                tenantId,
                projectId,
                mergeTargetId);
            requireMergeTargetUpdated(updated);
            softDelete("scene_asset", tenantId, projectId, elementId);
            return;
        }
        jdbcTemplate.update("""
            update scene_asset
               set status = 'CONFIRMED', merge_target_id = null, updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, tenantId, projectId, elementId);
    }

    private void confirmPropElement(Long tenantId, Long projectId, Long elementId) {
        Map<String, Object> element = jdbcTemplate.queryForMap("""
            select id, name, prop_type, appearance, plot_function, related_character, prompt, status, merge_target_id
              from prop_asset
             where tenant_id = ?
               and project_id = ?
               and id = ?
               and deleted_at is null
            """, tenantId, projectId, elementId);
        Long mergeTargetId = longValue(element.get("merge_target_id"));
        if ("PENDING_REVIEW".equals(stringValue(element.get("status"))) && mergeTargetId != null) {
            int updated = jdbcTemplate.update("""
                update prop_asset
                   set name = ?, prop_type = ?, appearance = ?, plot_function = ?, related_character = ?, prompt = ?, status = 'CONFIRMED', updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and status = 'CONFIRMED' and deleted_at is null
                """,
                stringValue(element.get("name")),
                stringValue(element.get("prop_type")),
                stringValue(element.get("appearance")),
                stringValue(element.get("plot_function")),
                stringValue(element.get("related_character")),
                stringValue(element.get("prompt")),
                tenantId,
                projectId,
                mergeTargetId);
            requireMergeTargetUpdated(updated);
            softDelete("prop_asset", tenantId, projectId, elementId);
            return;
        }
        jdbcTemplate.update("""
            update prop_asset
               set status = 'CONFIRMED', merge_target_id = null, updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, tenantId, projectId, elementId);
    }

    private void softDelete(String table, Long tenantId, Long projectId, Long elementId) {
        jdbcTemplate.update("""
            update %s
               set deleted_at = now(), updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """.formatted(table), tenantId, projectId, elementId);
    }

    private void requireMergeTargetUpdated(int updated) {
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "合并目标不存在或已失效，请重新提取后再确认。");
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}
