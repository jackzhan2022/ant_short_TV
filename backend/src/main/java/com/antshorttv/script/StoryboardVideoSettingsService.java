package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryboardVideoSettingsService {
    private static final Set<String> RESOLUTIONS = Set.of("480p","720p","1080p","4k","STANDARD");
    private final JdbcTemplate jdbc;
    private final ProjectPermissionGuard permissions;
    private final ObjectMapper json;
    public StoryboardVideoSettingsService(JdbcTemplate jdbc, ProjectPermissionGuard permissions, ObjectMapper json) {
        this.jdbc=jdbc; this.permissions=permissions; this.json=json;
    }
    @Transactional
    public Settings save(Long tenantId,Long projectId,Long storyboardId,Settings settings) {
        permissions.require(tenantId,projectId,"STORYBOARD:EDIT");
        if (settings == null || settings.durationSeconds() == null
            || settings.durationSeconds() != -1 && (settings.durationSeconds() < 1 || settings.durationSeconds() > 600)
            || settings.resolution() == null || !RESOLUTIONS.contains(settings.resolution().trim())
            || settings.generateAudio() == null || settings.watermark() == null)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,"视频生成参数不正确。");
        var rows=jdbc.queryForList("select id from storyboard where tenant_id=? and project_id=? and id=? and deleted_at is null for update",
            tenantId,projectId,storyboardId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"分镜不存在。");
        var saved=new Settings(settings.durationSeconds(),settings.resolution().trim(),settings.generateAudio(),settings.watermark());
        try {
            jdbc.update("update storyboard set video_generation_settings_json=?,updated_at=now() where tenant_id=? and project_id=? and id=? and deleted_at is null",
                json.writeValueAsString(saved),tenantId,projectId,storyboardId);
        } catch (JsonProcessingException exception) { throw new IllegalStateException("无法保存视频生成参数。",exception); }
        return saved;
    }
    @Transactional(readOnly=true)
    public Settings get(Long tenantId,Long projectId,Long storyboardId) {
        permissions.require(tenantId,projectId,"STORYBOARD:VIEW");
        var rows=jdbc.query("select video_generation_settings_json from storyboard where tenant_id=? and project_id=? and id=? and deleted_at is null",
            (rs,index) -> rs.getString(1),tenantId,projectId,storyboardId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"分镜不存在。");
        if (rows.get(0)==null || rows.get(0).isBlank()) return null;
        try { return json.readValue(rows.get(0),Settings.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("无法读取视频生成参数。",exception); }
    }
    public record Settings(@NotNull Integer durationSeconds,@NotBlank @Size(max=32) String resolution,
        @NotNull Boolean generateAudio,@NotNull Boolean watermark) {}
}
