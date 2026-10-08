package com.antshorttv.material;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StoryboardVideoDownloadService {
    private final JdbcTemplate jdbc;
    private final ProjectPermissionGuard permissions;
    private final MaterialFileAccessService files;
    private final ObjectStorageKeyFactory keys;

    public StoryboardVideoDownloadService(JdbcTemplate jdbc, ProjectPermissionGuard permissions,
        MaterialFileAccessService files, ObjectStorageKeyFactory keys) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.files = files;
        this.keys = keys;
    }

    public VideoDownload downloadStoryboard(Long projectId, Long storyboardId) {
        Long tenantId = requireProject(projectId);
        List<SelectedVideo> videos = selectedVideos(tenantId, projectId, "s.id", storyboardId);
        if (videos.isEmpty()) throw unavailable();
        SelectedVideo video = videos.get(0);
        requireDownloads(tenantId, projectId, videos);
        return new VideoDownload(files.resource(video.objectKey()),
            "第%02d集_分镜%03d.mp4".formatted(video.episodeNo(), video.shotNo()), video.fileSize());
    }

    public VideoDownload downloadEpisode(Long projectId, int episodeNo) throws IOException {
        if (episodeNo < 1) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择有效集数。");
        Long tenantId = requireProject(projectId);
        List<SelectedVideo> videos = selectedVideos(tenantId, projectId, "s.episode_no", episodeNo);
        if (videos.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "本集暂无可下载视频。");
        requireDownloads(tenantId, projectId, videos);
        Path archive = Files.createTempFile("antv-episode-videos-", ".zip");
        try {
            // Complete the archive before returning HTTP headers, so a failed object cannot produce a partial successful ZIP.
            try (var zip = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
                int index = 1;
                for (SelectedVideo video : videos) {
                    zip.putNextEntry(new ZipEntry("%03d_分镜%03d.mp4".formatted(index++, video.shotNo())));
                    try (var input = files.resource(video.objectKey()).getInputStream()) {
                        input.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
            return new VideoDownload(new TemporaryArchiveResource(archive),
                "第%02d集_全部分镜视频.zip".formatted(episodeNo), Files.size(archive));
        } catch (IOException | RuntimeException exception) {
            try { Files.deleteIfExists(archive); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    private Long requireProject(Long projectId) {
        var tenants = jdbc.query("select tenant_id from project where id=? and deleted_at is null",
            (row, index) -> row.getLong("tenant_id"), projectId);
        if (tenants.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在。");
        Long tenantId = tenants.get(0);
        permissions.require(tenantId, projectId, "PROJECT:VIEW");
        return tenantId;
    }

    private List<SelectedVideo> selectedVideos(Long tenantId, Long projectId, String selector, Object value) {
        // selector is only an internal constant; all user-supplied values remain bound parameters.
        return jdbc.query("""
            select s.episode_no,s.shot_no,s.current_video_url,s.current_shot_video_url,
                   r.storage_path raw_path,r.file_size raw_size,
                   c.storage_path composed_path,c.file_size composed_size
              from storyboard s
              left join ai_video_result r on r.id=s.current_video_result_id
               and r.tenant_id=s.tenant_id and r.project_id=s.project_id
               and r.storyboard_id=s.id and r.status='ACTIVE'
              left join shot_compose_result c on c.id=s.current_shot_result_id
               and c.tenant_id=s.tenant_id and c.project_id=s.project_id
               and c.storyboard_id=s.id and c.status='ACTIVE'
             where s.tenant_id=? and s.project_id=? and s.deleted_at is null
            """ + " and " + selector + "=? order by s.shot_no,s.id", (row, index) -> {
                String composedUrl = row.getString("current_shot_video_url");
                boolean composed = composedUrl != null && !composedUrl.isBlank();
                String visibleUrl = composed ? composedUrl : row.getString("current_video_url");
                if (visibleUrl == null || visibleUrl.isBlank()) return null;
                String path = row.getString(composed ? "composed_path" : "raw_path");
                if (path == null || path.isBlank()) return null;
                String normalized;
                try { normalized = keys.objectKey(path.startsWith("/") ? path.substring(1) : path); }
                catch (IllegalArgumentException invalid) { throw unavailable(); }
                if (!normalized.startsWith("materials/" + tenantId + "/" + projectId + "/")) throw unavailable();
                return new SelectedVideo(row.getInt("episode_no"), row.getInt("shot_no"), normalized,
                    row.getObject(composed ? "composed_size" : "raw_size", Long.class),
                    composed ? "SHOT_COMPOSE:DOWNLOAD" : "AI_VIDEO_RESULT:DOWNLOAD");
            }, tenantId, projectId, value).stream().filter(Objects::nonNull).toList();
    }

    private void requireDownloads(Long tenantId, Long projectId, List<SelectedVideo> videos) {
        videos.stream().map(SelectedVideo::permission).distinct()
            .forEach(permission -> permissions.require(tenantId, projectId, permission));
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.NOT_FOUND, "该分镜暂无可下载视频。");
    }

    public record VideoDownload(Resource resource, String fileName, Long fileSize) {}
    private record SelectedVideo(int episodeNo, int shotNo, String objectKey, Long fileSize, String permission) {}

    private static class TemporaryArchiveResource extends FileSystemResource {
        private final Path archive;
        TemporaryArchiveResource(Path archive) { super(archive); this.archive = archive; }
        @Override
        public InputStream getInputStream() throws IOException {
            return new FilterInputStream(super.getInputStream()) {
                @Override
                public void close() throws IOException {
                    try { super.close(); } finally { Files.deleteIfExists(archive); }
                }
            };
        }
    }
}
