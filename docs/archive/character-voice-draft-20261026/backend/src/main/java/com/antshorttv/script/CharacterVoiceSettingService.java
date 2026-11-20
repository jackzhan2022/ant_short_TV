package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CharacterVoiceSettingService {
    private final JdbcTemplate jdbc;
    private final ProjectPermissionGuard permissions;

    public CharacterVoiceSettingService(JdbcTemplate jdbc, ProjectPermissionGuard permissions) {
        this.jdbc = jdbc; this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<Setting> list(Long tenantId, Long projectId, List<Long> storyboardIds) {
        permissions.require(tenantId, projectId, "ELEMENT:VIEW");
        if (storyboardIds == null || storyboardIds.size() > 100 || storyboardIds.stream().anyMatch(id -> id == null || id <= 0))
            throw invalid("分镜音色请求最多包含100个有效分镜。");
        List<Long> ids = storyboardIds.stream().distinct().toList();
        if (!ids.isEmpty()) {
            permissions.require(tenantId, projectId, "STORYBOARD:VIEW");
            var args = new ArrayList<Object>(List.of(tenantId, projectId)); args.addAll(ids);
            Integer count = jdbc.queryForObject("select count(*) from storyboard where tenant_id=? and project_id=? and deleted_at is null and id in ("
                + placeholders(ids.size()) + ")", Integer.class, args.toArray());
            if (count == null || count != ids.size()) throw invalid("分镜不存在或不属于当前项目。");
        }
        return rows(jdbc, tenantId, projectId, ids).stream().map(row -> new Setting(
            ((Number) row.get("characterId")).longValue(), ((Number) row.get("storyboardId")).longValue(),
            ((Number) row.get("variantId")).longValue(), (String) row.get("voiceId"), (String) row.get("voiceName"))).toList();
    }

    @Transactional
    public Setting save(Long tenantId, Long projectId, Command command) {
        long shot = command.storyboardId() == null ? 0 : command.storyboardId();
        long variant = command.variantId() == null ? 0 : command.variantId();
        var actor = permissions.require(tenantId, projectId, shot == 0 ? "ELEMENT:EDIT" : "STORYBOARD:EDIT");
        validateScope(tenantId, projectId, command.characterId(), shot, variant);
        if (command.voiceId() == null || command.voiceId().isBlank() || command.voiceId().trim().length() > 128
            || command.voiceName() != null && command.voiceName().length() > 200) throw invalid("音色ID或名称不正确。");
        String name = command.voiceName() == null || command.voiceName().isBlank() ? null : command.voiceName().trim();
        String voiceId = command.voiceId().trim();
        var existing = jdbc.queryForList("""
            select id from character_voice_setting where tenant_id=? and project_id=? and character_id=? and storyboard_id=? and variant_id=? for update
            """, tenantId,projectId,command.characterId(),shot,variant);
        if (existing.isEmpty()) {
            jdbc.update("""
                insert into character_voice_setting (tenant_id,project_id,character_id,storyboard_id,variant_id,voice_id,voice_name,created_by,updated_by,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,now(),now())
                """, tenantId,projectId,command.characterId(),shot,variant,voiceId,name,actor.userId(),actor.userId());
        } else {
            jdbc.update("""
                update character_voice_setting set voice_id=?,voice_name=?,updated_by=?,updated_at=now()
                 where tenant_id=? and project_id=? and character_id=? and storyboard_id=? and variant_id=?
                """, voiceId,name,actor.userId(),tenantId,projectId,command.characterId(),shot,variant);
        }
        return new Setting(command.characterId(),shot,variant,voiceId,name);
    }

    @Transactional
    public void delete(Long tenantId, Long projectId, Long characterId, long storyboardId, long variantId) {
        permissions.require(tenantId, projectId, storyboardId == 0 ? "ELEMENT:EDIT" : "STORYBOARD:EDIT");
        validateScope(tenantId, projectId, characterId, storyboardId, variantId);
        jdbc.update("delete from character_voice_setting where tenant_id=? and project_id=? and character_id=? and storyboard_id=? and variant_id=?",
            tenantId,projectId,characterId,storyboardId,variantId);
    }

    private void validateScope(Long tenantId, Long projectId, Long characterId, long shot, long variant) {
        if (characterId == null || characterId <= 0 || shot < 0 || variant < 0 || shot == 0 && variant != 0)
            throw invalid("角色音色配置范围不正确。");
        // Serialize saves for this character, including concurrent first-time inserts.
        if (jdbc.queryForList("select id from character_asset where id=? and tenant_id=? and project_id=? and deleted_at is null for update",
            characterId,tenantId,projectId).isEmpty()) throw invalid("角色不存在或不属于当前项目。");
        if (shot > 0 && jdbc.queryForList("select id from storyboard where id=? and tenant_id=? and project_id=? and deleted_at is null",
            shot,tenantId,projectId).isEmpty()) throw invalid("分镜不存在或不属于当前项目。");
        if (variant > 0 && jdbc.queryForList("""
            select id from asset_visual_variant where id=? and tenant_id=? and project_id=? and asset_type='CHARACTER' and asset_id=? and deleted_at is null
            """, variant,tenantId,projectId,characterId).isEmpty()) throw invalid("形态不属于当前角色。");
    }

    static List<Map<String,Object>> rows(JdbcTemplate jdbc, Long tenantId, Long projectId, List<Long> ids) {
        var args = new ArrayList<Object>(List.of(tenantId, projectId)); args.addAll(ids);
        return jdbc.query("""
            select v.character_id,v.storyboard_id,v.variant_id,v.voice_id,v.voice_name
              from character_voice_setting v join character_asset c on c.id=v.character_id
               and c.tenant_id=v.tenant_id and c.project_id=v.project_id and c.deleted_at is null
             where v.tenant_id=? and v.project_id=? and (v.storyboard_id=0%s)
             order by v.character_id,v.storyboard_id,v.variant_id
            """.formatted(ids.isEmpty() ? "" : " or v.storyboard_id in (" + placeholders(ids.size()) + ")"), (rs, index) -> {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("characterId",rs.getLong("character_id")); row.put("storyboardId",rs.getLong("storyboard_id"));
                row.put("variantId",rs.getLong("variant_id")); row.put("voiceId",rs.getString("voice_id")); row.put("voiceName",rs.getString("voice_name"));
                return row;
            }, args.toArray());
    }
    private static String placeholders(int size) { return String.join(",", java.util.Collections.nCopies(size,"?")); }
    private static BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR,message); }
    public record Command(@NotNull Long characterId, Long storyboardId, Long variantId,
        @NotBlank @Size(max=128) String voiceId, @Size(max=200) String voiceName) {}
    public record Setting(Long characterId, Long storyboardId, Long variantId, String voiceId, String voiceName) {}
}
