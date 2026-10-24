package com.antshorttv.material;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.script.AssetNormalizationGuard;
import com.antshorttv.storage.*;
import com.qcloud.cos.COS;
import com.qcloud.cos.model.ciModel.mediaInfo.MediaInfoRequest;
import com.qcloud.cos.model.ciModel.common.MediaInputObject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAudioService {
    private final JdbcTemplate jdbc;
    private final ProjectPermissionGuard permissions;
    private final MediaUploadSessionService uploads;
    private final ObjectStorageService storage;
    private final MediaDeliveryGrantService grants;
    private final ObjectStorageProperties properties;
    private final COS cos;
    private final AssetNormalizationGuard normalization;
    private final ObjectStorageKeyFactory keys = new ObjectStorageKeyFactory();

    public ProjectAudioService(JdbcTemplate jdbc, ProjectPermissionGuard permissions,
        MediaUploadSessionService uploads, ObjectStorageService storage, MediaDeliveryGrantService grants,
        ObjectStorageProperties properties, COS cos, AssetNormalizationGuard normalization) {
        this.jdbc = jdbc; this.permissions = permissions; this.uploads = uploads;
        this.storage = storage; this.grants = grants; this.properties = properties;
        this.cos = cos; this.normalization = normalization;
    }

    @Transactional
    public AudioReference upload(Long tenantId, Long projectId, String sessionToken, String name) {
        var context = permissions.require(tenantId, projectId, "ELEMENT:EDIT");
        var upload = uploads.requireCompleted(context.userId(), tenantId, projectId, sessionToken);
        if (upload.size() <= 0 || upload.size() > 10 * 1024 * 1024
            || !Set.of("audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/wave").contains(upload.contentType()))
            throw invalidAudio();
        var existing = jdbc.queryForList("select id from project_audio_asset where tenant_id=? and project_id=? and upload_session_token=? and status='ACTIVE'",
            Long.class, tenantId, projectId, sessionToken);
        if (!existing.isEmpty()) return reference(tenantId, projectId, "PROJECT_AUDIO_ASSET", existing.get(0));
        BigDecimal duration = inspect(upload);
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                insert into project_audio_asset
                  (tenant_id,project_id,name,upload_session_token,storage_path,mime_type,file_size,duration_seconds,
                   status,created_by,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,'ACTIVE',?,current_timestamp,current_timestamp)
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId); statement.setLong(2, projectId); statement.setString(3, name);
            statement.setString(4, sessionToken); statement.setString(5, upload.objectKey());
            statement.setString(6, upload.contentType()); statement.setLong(7, upload.size());
            statement.setBigDecimal(8, duration); statement.setLong(9, context.userId());
            return statement;
        }, key);
        return reference(tenantId, projectId, "PROJECT_AUDIO_ASSET", key.getKey().longValue());
    }

    private BigDecimal inspect(VerifiedMediaUpload upload) {
        try {
            byte[] header;
            try (var stream = storage.resource(upload.objectKey()).getInputStream()) { header = stream.readNBytes(12); }
            boolean wav = header.length == 12 && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WAVE");
            boolean mp3 = header.length >= 3 && (new String(header, 0, 3, StandardCharsets.US_ASCII).equals("ID3")
                || (header[0] & 255) == 255 && (header[1] & 224) == 224);
            if (upload.contentType().contains("wav") ? !wav : !mp3) throw invalidAudio();
            var request = new MediaInfoRequest(); request.setBucketName(properties.getBucket());
            var input = new MediaInputObject(); input.setObject(upload.objectKey()); request.setInput(input);
            var streams = cos.generateMediainfo(request).getMediaInfo().getStream().getMediaInfoAudioList();
            if (streams == null || streams.isEmpty()) throw invalidAudio();
            var duration = new BigDecimal(streams.get(0).getDuration());
            if (duration.signum() <= 0 || duration.compareTo(new BigDecimal("999999999")) > 0) throw invalidAudio();
            return duration;
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw invalidAudio(); }
    }

    @Transactional(readOnly = true)
    public AudioReference current(Long tenantId, Long projectId, Long characterId) {
        permissions.require(tenantId, projectId, "ELEMENT:VIEW");
        long canonical = requireCharacter(tenantId, projectId, characterId, false);
        var rows = jdbc.query("select source_project_id,resource_type,resource_id from character_voice_reference where tenant_id=? and project_id=? and character_id=?",
            (rs, index) -> new Source(rs.getLong(1), rs.getString(2), rs.getLong(3)), tenantId, projectId, canonical);
        if (rows.isEmpty()) return null;
        var source = rows.get(0);
        try { return reference(tenantId, source.projectId(), source.type(), source.id()); }
        catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.NOT_FOUND) return null;
            throw exception;
        }
    }

    @Transactional
    public AudioReference bind(Long tenantId, Long projectId, Long characterId, Long sourceProjectId, String type, Long id) {
        var context = permissions.require(tenantId, projectId, "ELEMENT:EDIT");
        normalization.requireReady(tenantId, projectId);
        long canonical = requireCharacter(tenantId, projectId, characterId, true);
        var audio = reference(tenantId, sourceProjectId, type, id);
        jdbc.update("""
            insert into character_voice_reference
              (tenant_id,project_id,character_id,source_project_id,resource_type,resource_id,updated_by,created_at,updated_at)
            values (?,?,?,?,?,?,?,current_timestamp,current_timestamp)
            on duplicate key update source_project_id=values(source_project_id),resource_type=values(resource_type),
              resource_id=values(resource_id),updated_by=values(updated_by),updated_at=current_timestamp
            """, tenantId, projectId, canonical, sourceProjectId, type, id, context.userId());
        return audio;
    }

    private long requireCharacter(Long tenantId, Long projectId, Long id, boolean lock) {
        long canonical = AssetNormalizationGuard.canonicalId(jdbc, tenantId, projectId, "CHARACTER", id);
        var ids = jdbc.queryForList("select id from character_asset where tenant_id=? and project_id=? and id=? and deleted_at is null"
            + (lock ? " for update" : ""), Long.class, tenantId, projectId, canonical);
        if (ids.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在。");
        return canonical;
    }

    public AudioReference reference(Long tenantId, Long projectId, String type, Long id) {
        permissions.require(tenantId, projectId, "AI_VOICE_TASK:VIEW");
        var audio = resource(tenantId, projectId, type, id);
        String name = jdbc.queryForObject("select name from project where tenant_id=? and id=? and deleted_at is null", String.class, tenantId, projectId);
        return new AudioReference(projectId, name, type, id, audio.name(), playbackPath(projectId, type, id), audio.duration());
    }

    public TrustedAudio trustedMedia(Long tenantId, Long projectId, String type, Long id) {
        permissions.require(tenantId, projectId, "AI_VOICE_TASK:VIEW");
        var audio = resource(tenantId, projectId, type, id);
        String format = audio.format();
        if (format == null || format.isBlank()) {
            int dot = audio.key().lastIndexOf('.');
            format = dot < 0 ? "" : audio.key().substring(dot + 1);
        }
        format = format.toLowerCase(java.util.Locale.ROOT).replace("audio/", "");
        format = switch (format) { case "mpeg", "mp3" -> "mp3"; case "x-wav", "wave" -> "wav"; default -> format; };
        return new TrustedAudio(audio.name(), audio.key().startsWith("/") ? audio.key().substring(1) : audio.key(),
            format, audio.fileSize(), audio.duration(), playbackPath(projectId, type, id));
    }

    public DeliveryGrant playback(Long projectId, String type, Long id) {
        // Native audio elements send session cookies but cannot attach X-Tenant-Id.
        var tenants = jdbc.queryForList("select tenant_id from project where id=? and deleted_at is null", Long.class, projectId);
        if (tenants.isEmpty()) throw unavailable();
        return playback(tenants.get(0), projectId, type, id);
    }

    public DeliveryGrant playback(Long tenantId, Long projectId, String type, Long id) {
        var context = permissions.require(tenantId, projectId, "AI_VOICE_TASK:VIEW");
        var audio = resource(tenantId, projectId, type, id);
        String key = audio.key().startsWith("/") ? audio.key().substring(1) : audio.key();
        if (!key.startsWith("materials/" + tenantId + "/" + projectId + "/")) throw unavailable();
        return grants.issue(new DeliveryGrantRequest(tenantId, projectId, context.userId(), type, id,
            "audio-" + id, "AUDIO", key, false));
    }

    private AudioResource resource(Long tenantId, Long projectId, String type, Long id) {
        String sql;
        if ("PROJECT_AUDIO_ASSET".equals(type)) sql = "select name,storage_path,duration_seconds,file_size,mime_type from project_audio_asset where tenant_id=? and project_id=? and id=? and status='ACTIVE'";
        else if ("AI_VOICE_RESULT".equals(type)) sql = """
            select concat(coalesce(t.speaker_name,'语音'),' · EP',s.episode_no,' 镜头 ',s.shot_no) as name,r.storage_path,r.duration_seconds,r.file_size,r.format
              from ai_voice_result r join ai_voice_task t on t.id=r.task_id and t.tenant_id=r.tenant_id
               and t.project_id=r.project_id and t.deleted_at is null
              join storyboard s on s.id=r.storyboard_id and s.tenant_id=r.tenant_id and s.project_id=r.project_id and s.deleted_at is null
             where r.tenant_id=? and r.project_id=? and r.id=? and r.status='ACTIVE'
            """;
        else throw new BusinessException(ErrorCode.VALIDATION_ERROR, "音频类型不正确。");
        List<AudioResource> resources = jdbc.query(sql, (rs, index) -> new AudioResource(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getObject(4, Long.class), rs.getString(5)), tenantId, projectId, id);
        if (resources.isEmpty() || resources.get(0).key() == null || resources.get(0).key().isBlank()) throw unavailable();
        var audio = resources.get(0);
        try {
            String key = keys.objectKey(audio.key().startsWith("/") ? audio.key().substring(1) : audio.key());
            if (!key.startsWith("materials/" + tenantId + "/" + projectId + "/")) throw unavailable();
        } catch (IllegalArgumentException exception) { throw unavailable(); }
        return audio;
    }

    public static String playbackPath(Long projectId, String type, Long id) {
        return "/api/projects/" + projectId + ("PROJECT_AUDIO_ASSET".equals(type) ? "/audio-assets/" : "/ai-voice-results/") + id + "/playback";
    }
    private BusinessException unavailable() { return new BusinessException(ErrorCode.NOT_FOUND, "音频不存在或已失效。"); }
    private BusinessException invalidAudio() { return new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择不超过10MB的有效 MP3 或 WAV 音频。"); }
    private record Source(Long projectId, String type, Long id) {}
    private record AudioResource(String name, String key, BigDecimal duration, Long fileSize, String format) {}
    public record TrustedAudio(String name, String storagePath, String format, Long fileSize, BigDecimal durationSeconds, String previewUrl) {}
    public record AudioReference(Long sourceProjectId, String sourceProjectName, String resourceType, Long resourceId,
        String name, String previewUrl, BigDecimal durationSeconds) {}
}
