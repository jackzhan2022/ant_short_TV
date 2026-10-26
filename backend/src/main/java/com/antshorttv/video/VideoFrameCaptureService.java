package com.antshorttv.video;

import com.antshorttv.aiimage.AiImageResultEntity;
import com.antshorttv.aiimage.AiImageResultMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.*;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoFrameCaptureService {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final MediaUploadSessionService uploads;
    private final ObjectStorageService storage;
    private final ObjectStorageKeyFactory keys;
    private final ObjectStorageProperties properties;
    private final ImageDisplayRenditionService renditions;
    private final AiImageResultMapper images;

    public VideoFrameCaptureService(JdbcTemplate jdbc, MediaUploadSessionService uploads,
        ObjectStorageService storage, ObjectStorageKeyFactory keys, ObjectStorageProperties properties,
        ImageDisplayRenditionService renditions, AiImageResultMapper images) {
        this.jdbc=jdbc; this.uploads=uploads; this.storage=storage; this.keys=keys;
        this.properties=properties; this.renditions=renditions; this.images=images;
    }

    public Long sourceTenantId(Long projectId, Long resultId) {
        var tenants = jdbc.query("""
            select tenant_id from ai_video_result where id=? and project_id=? and status='ACTIVE'
            """, (rs, row) -> rs.getLong("tenant_id"), resultId, projectId);
        if (tenants.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "可用的分镜视频不存在。");
        return tenants.get(0);
    }
    public Source source(Long tenantId, Long projectId, Long resultId) {
        var sources=jdbc.query("""
            select r.storage_path,r.duration_seconds,r.storyboard_id
              from ai_video_result r
              join ai_video_task t on t.id=r.task_id and t.tenant_id=r.tenant_id
                   and t.project_id=r.project_id and t.deleted_at is null
              join storyboard s on s.id=r.storyboard_id and s.tenant_id=r.tenant_id
                   and s.project_id=r.project_id and s.deleted_at is null
             where r.id=? and r.tenant_id=? and r.project_id=? and r.status='ACTIVE'
            """,(rs,row) -> new Source(rs.getString("storage_path"),rs.getObject("duration_seconds",Double.class),
                rs.getLong("storyboard_id")),resultId,tenantId,projectId);
        if(sources.isEmpty() || sources.get(0).storagePath()==null || sources.get(0).storagePath().isBlank())
            throw new BusinessException(ErrorCode.NOT_FOUND,"可用的分镜视频不存在。");
        Source source = sources.get(0);
        String path = source.storagePath();
        String key;
        try {
            key = keys.objectKey(path.startsWith("/") ? path.substring(1) : path);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "可用的分镜视频不存在。");
        }
        if (!key.startsWith("materials/" + tenantId + "/" + projectId + "/"))
            throw new BusinessException(ErrorCode.NOT_FOUND, "可用的分镜视频不存在。");
        return new Source(key, source.durationSeconds(), source.storyboardId());
    }

    @Transactional
    public FrameResponse save(Long tenantId, Long projectId, Long resultId, Long userId,
        String sessionToken, double timeSeconds) {
        Source source=source(tenantId,projectId,resultId);
        if(!Double.isFinite(timeSeconds) || timeSeconds<0 || source.durationSeconds()==null
            || !Double.isFinite(source.durationSeconds()) || source.durationSeconds()<=0
            || timeSeconds>source.durationSeconds())
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,"截帧时间必须在视频时长范围内。");
        var upload=uploads.requireCompleted(userId,tenantId,projectId,sessionToken);
        // Serialize retries across application instances. The current read below also sees a prior commit
        // under MySQL REPEATABLE READ even if source() established the transaction snapshot earlier.
        var locked=jdbc.queryForList("""
            select id from media_upload_session where session_token=? and tenant_id=? and project_id=?
                and user_id=? and status='COMPLETED' for update
            """,sessionToken,tenantId,projectId,userId);
        if(locked.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR,"上传会话不可用。");
        var existing=jdbc.query("""
            select f.image_result_id,r.image_url,r.thumbnail_url,f.frame_time_seconds,f.source_video_result_id,r.status
              from video_frame_capture f join ai_image_result r on r.id=f.image_result_id
             where f.session_token=? and f.tenant_id=? and f.project_id=? for update
            """,(rs,row) -> {
                if(!"ACTIVE".equals(rs.getString("status")))
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,"该上传已保存的图片不再可用。");
                return new FrameResponse(rs.getLong("image_result_id"),rs.getString("image_url"),
                    rs.getString("thumbnail_url"),rs.getDouble("frame_time_seconds"),rs.getLong("source_video_result_id"));
            },sessionToken,tenantId,projectId);
        if(!existing.isEmpty()) {
            var result=existing.get(0);
            if(!result.sourceVideoResultId().equals(resultId) || Double.compare(result.timeSeconds(),timeSeconds)!=0)
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,"同一上传会话不能用于不同的视频或截帧时间。");
            return result;
        }
        if(!"image/png".equals(upload.contentType()) || upload.size()<=0 || upload.size()>MAX_BYTES) throw invalidPng();
        int[] size=validatePng(upload);
        String key=keys.projectOriginal(tenantId,projectId,"images",resultId,
            "frame-"+sessionToken,LocalDate.of(2000,1,1),"png");
        var original=storage.copyCompletedUploadOriginal(new StoredObject(upload.objectKey(),upload.size(),
            "image/png",upload.eTag(),properties.getStorageClass()),key);
        var image=new AiImageResultEntity();
        image.setTenantId(tenantId); image.setProjectId(projectId); image.setTargetType("VIDEO_FRAME");
        image.setTargetId(source.storyboardId()); image.setImageUrl(""); image.setStoragePath(key);
        image.setMimeType("image/png"); image.setWidth(size[0]); image.setHeight(size[1]);
        image.setFileSize(upload.size()); image.setIsSelected(false); image.setStatus("ACTIVE");
        image.setCreatedAt(LocalDateTime.now()); image.setUpdatedAt(LocalDateTime.now());
        images.insert(image);
        var registered=renditions.registerOriginalAndSubmit(new MediaObjectIdentity(tenantId,projectId,
            "AI_IMAGE_RESULT",image.getId(),"result-"+image.getId()),original,size[0],size[1],"video-frame:"+image.getId());
        String base="/api/projects/"+projectId+"/ai-image-results/"+image.getId();
        image.setImageUrl(base+"/display"); image.setThumbnailUrl(base+"/thumbnail");
        image.setDisplayPath(registered.displayKey()); image.setThumbnailPath(registered.displayKey());
        images.updateById(image);
        jdbc.update("""
            insert into video_frame_capture (image_result_id,tenant_id,project_id,source_video_result_id,
                storyboard_id,frame_time_seconds,session_token,created_by,created_at)
            values (?,?,?,?,?,?,?,?,?)
            """,image.getId(),tenantId,projectId,resultId,source.storyboardId(),timeSeconds,sessionToken,userId,LocalDateTime.now());
        return new FrameResponse(image.getId(),image.getImageUrl(),image.getThumbnailUrl(),timeSeconds,resultId);
    }

    private int[] validatePng(VerifiedMediaUpload upload) {
        try {
            byte[] bytes;
            try(var input=storage.resource(upload.objectKey()).getInputStream()) { bytes=input.readNBytes(MAX_BYTES+1); }
            if(bytes.length!=upload.size() || bytes.length>MAX_BYTES) throw invalidPng();
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw invalidPng();
                var reader=readers.next();
                try {
                    reader.setInput(input);
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(!"png".equalsIgnoreCase(reader.getFormatName()) || width<=0 || height<=0
                        || (long)width*height>40_000_000L || reader.read(0)==null) throw invalidPng();
                    return new int[]{width,height};
                } finally { reader.dispose(); }
            }
        } catch(BusinessException exception) { throw exception; }
        catch(Exception exception) { throw invalidPng(); }
    }
    private BusinessException invalidPng() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR,"请上传不超过20MB、4000万像素的有效PNG视频帧。");
    }
    public record Source(String storagePath, Double durationSeconds, Long storyboardId) {}
    public record FrameResponse(Long id,String imageUrl,String thumbnailUrl,double timeSeconds,Long sourceVideoResultId) {}
}
