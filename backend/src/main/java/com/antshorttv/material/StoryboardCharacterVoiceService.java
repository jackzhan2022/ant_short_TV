package com.antshorttv.material;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryboardCharacterVoiceService {
    private final JdbcTemplate jdbc;
    private final ProjectPermissionGuard permissions;
    private final ProjectAudioService audio;

    public StoryboardCharacterVoiceService(JdbcTemplate jdbc, ProjectPermissionGuard permissions, ProjectAudioService audio) {
        this.jdbc = jdbc; this.permissions = permissions; this.audio = audio;
    }

    public EffectiveVoice current(Long tenant, Long project, Long storyboard, Long character, Long variant) {
        requireBinding(tenant, project, storyboard, character, variant, "STORYBOARD:VIEW");
        var choices = choicesForVideo(tenant, project, storyboard).stream()
            .filter(choice -> choice.characterId().equals(character) && java.util.Objects.equals(choice.variantId(), variant)).toList();
        if (choices.isEmpty()) return new EffectiveVoice(null, false);
        var choice = choices.get(0);
        return new EffectiveVoice(audio.reference(tenant, choice.sourceProjectId(), choice.resourceType(), choice.resourceId()), choice.overridden());
    }

    @Transactional
    public EffectiveVoice bind(Long tenant, Long project, Long storyboard, Long character, Long variant,
        Long sourceProject, String type, Long resourceId) {
        var user = permissions.require(tenant, project, "STORYBOARD:EDIT");
        requireBinding(tenant, project, storyboard, character, variant, "STORYBOARD:EDIT");
        var reference = audio.reference(tenant, sourceProject, type, resourceId);
        jdbc.update("""
            insert into storyboard_character_voice
              (tenant_id,project_id,storyboard_id,character_id,variant_id,source_project_id,resource_type,resource_id,updated_by,created_at,updated_at)
            values (?,?,?,?,?,?,?,?,?,current_timestamp,current_timestamp)
            on duplicate key update source_project_id=values(source_project_id),resource_type=values(resource_type),
              resource_id=values(resource_id),updated_by=values(updated_by),updated_at=current_timestamp
            """, tenant, project, storyboard, character, variant == null ? 0L : variant, sourceProject, type, resourceId, user.userId());
        return new EffectiveVoice(reference, true);
    }

    @Transactional
    public EffectiveVoice reset(Long tenant, Long project, Long storyboard, Long character, Long variant) {
        requireBinding(tenant, project, storyboard, character, variant, "STORYBOARD:EDIT");
        jdbc.update("delete from storyboard_character_voice where tenant_id=? and project_id=? and storyboard_id=? and character_id=? and variant_id=?",
            tenant, project, storyboard, character, variant == null ? 0L : variant);
        return current(tenant, project, storyboard, character, variant);
    }

    private void requireBinding(Long tenant, Long project, Long storyboard, Long character, Long variant, String permission) {
        permissions.require(tenant, project, permission);
        var rows = jdbc.queryForList("""
            select r.id from storyboard_asset_reference r
              join storyboard s on s.id=r.storyboard_id and s.tenant_id=r.tenant_id and s.project_id=r.project_id and s.deleted_at is null
              join character_asset c on c.id=r.asset_id and c.tenant_id=r.tenant_id and c.project_id=r.project_id and c.deleted_at is null
             where r.tenant_id=? and r.project_id=? and r.storyboard_id=? and r.asset_type='CHARACTER'
               and r.asset_id=? and coalesce(r.variant_id,0)=? and r.retired_at is null
            """, Long.class, tenant, project, storyboard, character, variant == null ? 0L : variant);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "当前分镜未绑定该角色形态，请刷新后重试。");
    }

    /** Called by video preparation only after authorizing the target storyboard; source audio is authorized separately. */
    public List<VoiceChoice> choicesForVideo(Long tenant, Long project, Long storyboard) {
        return jdbc.query("""
            select r.asset_id,c.name as character_name,r.variant_id,v.name as variant_name,r.reference_role,r.sort_order,
                   coalesce(o.source_project_id,d.source_project_id) as source_project_id,
                   coalesce(o.resource_type,d.resource_type) as resource_type,
                   coalesce(o.resource_id,d.resource_id) as resource_id,
                   case when o.id is null then false else true end as overridden
              from storyboard_asset_reference r
              join storyboard s on s.id=r.storyboard_id and s.tenant_id=r.tenant_id and s.project_id=r.project_id and s.deleted_at is null
              join character_asset c on c.id=r.asset_id and c.tenant_id=r.tenant_id and c.project_id=r.project_id and c.deleted_at is null
              left join asset_visual_variant v on v.id=r.variant_id and v.tenant_id=r.tenant_id and v.project_id=r.project_id and v.deleted_at is null
              left join storyboard_character_voice o on o.tenant_id=r.tenant_id and o.project_id=r.project_id
                and o.storyboard_id=r.storyboard_id and o.character_id=r.asset_id and o.variant_id=coalesce(r.variant_id,0)
              left join character_voice_reference d on d.tenant_id=r.tenant_id and d.project_id=r.project_id and d.character_id=r.asset_id
             where r.tenant_id=? and r.project_id=? and r.storyboard_id=? and r.asset_type='CHARACTER'
               and r.retired_at is null and (o.id is not null or d.id is not null)
             order by r.sort_order,r.id
            """, (rs, index) -> new VoiceChoice(rs.getLong("asset_id"), rs.getString("character_name"),
                rs.getObject("variant_id", Long.class), rs.getString("variant_name"), rs.getString("reference_role"),
                rs.getInt("sort_order"), rs.getLong("source_project_id"), rs.getString("resource_type"), rs.getLong("resource_id"), rs.getBoolean("overridden")),
            tenant, project, storyboard);
    }

    public record EffectiveVoice(ProjectAudioService.AudioReference audio, boolean overridden) {}
    public record VoiceChoice(Long characterId, String characterName, Long variantId, String variantName,
        String referenceRole, int sortOrder, Long sourceProjectId, String resourceType, Long resourceId, boolean overridden) {}
}
