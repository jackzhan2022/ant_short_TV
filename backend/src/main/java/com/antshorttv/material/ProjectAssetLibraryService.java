package com.antshorttv.material;

import com.antshorttv.rbac.MenuAuthorization;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.common.MediaPage;
import com.antshorttv.common.PageBounds;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.rbac.ProjectPermissionGuard;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAssetLibraryService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessResolver access;
    private final ProjectPermissionGuard permissions;

    public ProjectAssetLibraryService(JdbcTemplate jdbc, ProjectAccessResolver access, ProjectPermissionGuard permissions) {
        this.jdbc = jdbc;
        this.access = access;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public LibraryResponse list(Long tenantId, Long projectId, String mediaType, String category,
        Integer episodeNo, String keyword, Integer current, Integer pageSize) {
        if (!Set.of("IMAGE", "VIDEO", "AUDIO").contains(mediaType)
            || category != null && !("AUDIO".equals(mediaType) ? Set.of("ALL", "REFERENCE", "GENERATED")
                : Set.of("ALL", "CHARACTER", "SCENE", "PROP", "OTHER", "VIDEO_FRAME")).contains(category))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资产类型不正确。");
        var context = access.requireView(tenantId, projectId);
        Set<String> codes = context.effectivePermissions();
        List<Object> args = new ArrayList<>();
        String base;
        if ("IMAGE".equals(mediaType)) {
            permissions.require(context, "AI_IMAGE_TASK:VIEW");
            base = imageQuery();
            args.add(tenantId);
            args.add(projectId);
        } else if ("AUDIO".equals(mediaType)) {
            permissions.require(context, "AI_VOICE_TASK:VIEW");
            base = audioQuery();
            args.add(tenantId); args.add(projectId); args.add(tenantId); args.add(projectId);
        } else {
            List<String> queries = new ArrayList<>();
            if (MenuAuthorization.allows(codes, "AI_VIDEO_TASK:VIEW")) {
                queries.add(shotVideoQuery("AI_VIDEO_RESULT", "ai_video_result", "ai_video_task", "项目生成"));
                args.add(tenantId); args.add(projectId);
            }
            if (MenuAuthorization.allows(codes, "SHOT_COMPOSE:VIEW")) {
                queries.add(shotVideoQuery("SHOT_COMPOSE_RESULT", "shot_compose_result", "shot_compose_task", "镜头合成"));
                args.add(tenantId); args.add(projectId);
            }
            if (MenuAuthorization.allows(codes, "EPISODE_VERSION:VIEW")) {
                queries.add("""
                    select r.id, 'EPISODE_VIDEO_VERSION' as resource_type, 'OTHER' as category,
                           r.version_name as asset_name, '' as variant_name, '剧集合成' as source_type,
                           r.is_current as current_image, r.episode_no, null as shot_no,
                           r.duration_seconds, r.updated_at
                      from episode_video_version r
                      join episode_compose_task t on t.id=r.compose_task_id and t.tenant_id=r.tenant_id
                           and t.project_id=r.project_id and t.deleted_at is null
                     where r.tenant_id=? and r.project_id=? and r.status='ACTIVE'
                    """);
                args.add(tenantId); args.add(projectId);
            }
            if (queries.isEmpty()) throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看项目视频资产。");
            base = String.join(" union all ", queries);
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("select category, count(*) as amount from (" + base + ") library group by category",
            (rs, row) -> Map.entry(rs.getString("category"), rs.getLong("amount")), args.toArray())
            .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        List<Integer> episodes = "VIDEO".equals(mediaType)
            ? jdbc.query("select distinct episode_no from (" + base + ") library where episode_no is not null order by episode_no",
                (rs, row) -> rs.getInt(1), args.toArray()) : List.of();
        String filter = " where 1=1";
        if (!"VIDEO".equals(mediaType) && category != null && !"ALL".equals(category)) {
            filter += " and category=?"; args.add(category);
        }
        if (episodeNo != null) { filter += " and episode_no=?"; args.add(episodeNo); }
        if (keyword != null && !keyword.isBlank()) {
            filter += " and lower(concat(asset_name,' ',variant_name)) like ? escape '!'";
            args.add("%" + keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        Long total = jdbc.queryForObject("select count(*) from (" + base + ") library" + filter, Long.class, args.toArray());
        PageBounds bounds = PageBounds.of(current, pageSize);
        List<LibraryItem> items = List.of();
        if (total != null && bounds.offset() < total) {
            args.add(bounds.pageSize()); args.add(bounds.offset());
            items = jdbc.query("select * from (" + base + ") library" + filter
                + " order by updated_at desc, resource_type, id desc limit ? offset ?",
                (rs, row) -> item(projectId, rs, codes), args.toArray());
        }
        return new LibraryResponse(new MediaPage<>(items, bounds.current(), bounds.pageSize(), total == null ? 0 : total), episodes, counts);
    }

    private String audioQuery() {
        return """
            select r.id,'PROJECT_AUDIO_ASSET' as resource_type,'REFERENCE' as category,r.name as asset_name,
                   '' as variant_name,'本地上传' as source_type,false as current_image,null as episode_no,
                   null as shot_no,r.duration_seconds,r.updated_at
              from project_audio_asset r where r.tenant_id=? and r.project_id=? and r.status='ACTIVE'
            union all
            select r.id,'AI_VOICE_RESULT' as resource_type,'GENERATED' as category,
                   concat(coalesce(t.speaker_name,'语音'),' · EP',s.episode_no,' 镜头 ',s.shot_no) as asset_name,
                   '' as variant_name,'项目生成' as source_type,r.is_selected as current_image,
                   s.episode_no,s.shot_no,r.duration_seconds,r.updated_at
              from ai_voice_result r
              join ai_voice_task t on t.id=r.task_id and t.tenant_id=r.tenant_id and t.project_id=r.project_id and t.deleted_at is null
              join storyboard s on s.id=r.storyboard_id and s.tenant_id=r.tenant_id and s.project_id=r.project_id and s.deleted_at is null
             where r.tenant_id=? and r.project_id=? and r.status='ACTIVE' and r.storage_path is not null and r.storage_path<>''
            """;
    }

    private String imageQuery() {
        return """
            select r.id, 'AI_IMAGE_RESULT' as resource_type,
                   case when r.target_type='VIDEO_FRAME' then 'VIDEO_FRAME'
                        when r.target_type='VISUAL_VARIANT' then v.asset_type
                        when r.target_type in ('CHARACTER','SCENE','PROP') then r.target_type else 'OTHER' end as category,
                   case when r.target_type='VIDEO_FRAME' then concat('EP',s.episode_no,' · 分镜 ',s.shot_no,
                        ' · 视频版本 #',f.source_video_result_id,' · ',f.frame_time_seconds,'s')
                        when r.target_type='VIDEO_COVER' then concat('EP',s.episode_no,' · 镜头 ',s.shot_no,' · 视频封面 ',r.target_id)
                        else coalesce(c.name,sc.name,p.name,concat('分镜 ',s.shot_no)) end as asset_name,
                   coalesce(v.name,'') as variant_name,
                   case when r.target_type in ('VIDEO_FRAME','VIDEO_COVER') then '视频截帧'
                        when r.task_id is null then '本地上传' else '项目生成' end as source_type,
                   case when v.current_image_result_id=r.id or c.main_image_result_id=r.id
                         or sc.main_image_result_id=r.id or p.main_image_result_id=r.id or s.first_frame_result_id=r.id
                        then true else false end as current_image,
                   s.episode_no, s.shot_no, null as duration_seconds, r.updated_at
              from ai_image_result r
              left join ai_image_task t on t.id=r.task_id and t.tenant_id=r.tenant_id and t.project_id=r.project_id
              left join asset_visual_variant v on r.target_type='VISUAL_VARIANT' and v.id=r.target_id
                   and v.tenant_id=r.tenant_id and v.project_id=r.project_id and v.deleted_at is null
              left join character_asset c on c.id=case when r.target_type='CHARACTER' then r.target_id
                    when v.asset_type='CHARACTER' then v.asset_id else null end
                   and c.tenant_id=r.tenant_id and c.project_id=r.project_id and c.deleted_at is null
              left join scene_asset sc on sc.id=case when r.target_type='SCENE' then r.target_id
                    when v.asset_type='SCENE' then v.asset_id else null end
                   and sc.tenant_id=r.tenant_id and sc.project_id=r.project_id and sc.deleted_at is null
              left join prop_asset p on p.id=case when r.target_type='PROP' then r.target_id
                    when v.asset_type='PROP' then v.asset_id else null end
                   and p.tenant_id=r.tenant_id and p.project_id=r.project_id and p.deleted_at is null
              left join video_frame_capture f on r.target_type='VIDEO_FRAME' and f.image_result_id=r.id
                   and f.tenant_id=r.tenant_id and f.project_id=r.project_id
              left join ai_video_task cover_task on r.target_type='VIDEO_COVER' and cover_task.id=r.target_id
                   and cover_task.tenant_id=r.tenant_id and cover_task.project_id=r.project_id and cover_task.deleted_at is null
              left join storyboard s on s.id=case when r.target_type='STORYBOARD' then r.target_id
                   when r.target_type='VIDEO_FRAME' then f.storyboard_id
                   when r.target_type='VIDEO_COVER' then cover_task.storyboard_id else null end
                   and s.tenant_id=r.tenant_id and s.project_id=r.project_id and s.deleted_at is null
             where r.tenant_id=? and r.project_id=? and r.status='ACTIVE'
               and r.display_path is not null and r.display_path<>''
               and (r.task_id is null or t.id is not null and t.deleted_at is null)
               and (c.id is not null or sc.id is not null or p.id is not null or s.id is not null)
            """;
    }

    private String shotVideoQuery(String kind, String table, String taskTable, String source) {
        return """
            select r.id, '%s' as resource_type, 'OTHER' as category,
                   concat('EP',s.episode_no,' · 镜头 ',s.shot_no) as asset_name,
                   '' as variant_name, '%s' as source_type, r.is_selected as current_image,
                   s.episode_no, s.shot_no, r.duration_seconds, r.updated_at
              from %s r
              join %s t on t.id=r.task_id and t.tenant_id=r.tenant_id and t.project_id=r.project_id and t.deleted_at is null
              join storyboard s on s.id=r.storyboard_id and s.tenant_id=r.tenant_id
                   and s.project_id=r.project_id and s.deleted_at is null
             where r.tenant_id=? and r.project_id=? and r.status='ACTIVE'
            """.formatted(kind, source, table, taskTable);
    }

    private LibraryItem item(Long projectId, ResultSet row, Set<String> permissions) throws SQLException {
        long id = row.getLong("id");
        String kind = row.getString("resource_type");
        boolean image = "AI_IMAGE_RESULT".equals(kind);
        boolean audio = Set.of("PROJECT_AUDIO_ASSET", "AI_VOICE_RESULT").contains(kind);
        String resource = switch (kind) {
            case "AI_IMAGE_RESULT" -> "ai-image-results";
            case "AI_VIDEO_RESULT" -> "ai-video-results";
            case "SHOT_COMPOSE_RESULT" -> "shot-compose-results";
            case "EPISODE_VIDEO_VERSION" -> "episode-video-versions";
            case "PROJECT_AUDIO_ASSET" -> "audio-assets";
            case "AI_VOICE_RESULT" -> "ai-voice-results";
            default -> throw new IllegalStateException("Unknown media kind");
        };
        String base = "/api/projects/" + projectId + "/" + resource + "/" + id;
        String name = row.getString("asset_name");
        String variant = row.getString("variant_name");
        if (variant != null && !variant.isBlank()) name += " · " + variant;
        String download = image ? base + "/download"
            : "AI_VIDEO_RESULT".equals(kind) && MenuAuthorization.allows(permissions, "AI_VIDEO_RESULT:DOWNLOAD") ? base + "/download-file" : null;
        return new LibraryItem(id, kind, name, row.getString("category"), row.getString("source_type"),
            audio ? "" : base + (image ? "/thumbnail" : "/cover"), base + (image ? "/display" : "/playback"), download,
            row.getObject("episode_no", Integer.class), row.getObject("shot_no", Integer.class),
            row.getObject("duration_seconds", Double.class), row.getBoolean("current_image"),
            row.getTimestamp("updated_at").toLocalDateTime());
    }

    public record LibraryItem(Long id, String resourceType, String name, String category, String sourceType,
        String thumbnailUrl, String previewUrl, String downloadUrl, Integer episodeNo, Integer shotNo,
        Double durationSeconds, boolean current, LocalDateTime updatedAt) {}
    public record LibraryResponse(MediaPage<LibraryItem> items, List<Integer> episodeNumbers, Map<String, Long> categoryCounts) {}
}
