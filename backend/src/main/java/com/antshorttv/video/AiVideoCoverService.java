package com.antshorttv.video;

import com.antshorttv.aiimage.AiImageResultEntity;
import com.antshorttv.aiimage.AiImageResultMapper;
import com.antshorttv.storage.CloudInfiniteVideoCoverService;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** Captures a video cover outside the video publication transaction. */
@Service
public class AiVideoCoverService {
    private final AiImageResultMapper images;
    private final CloudInfiniteVideoCoverService covers;
    private final ImageDisplayRenditionService renditions;
    private final ObjectStorageKeyFactory keys;

    public AiVideoCoverService(AiImageResultMapper images, CloudInfiniteVideoCoverService covers,
        ImageDisplayRenditionService renditions, ObjectStorageKeyFactory keys) {
        this.images = images;
        this.covers = covers;
        this.renditions = renditions;
        this.keys = keys;
    }

    public AiImageResultEntity prepare(AiVideoTaskEntity task, AiVideoResultEntity video) {
        return prepare(task, video, "VIDEO_COVER", task.id);
    }

    public AiImageResultEntity prepareProcessed(AiVideoTaskEntity task, AiVideoResultEntity video) {
        return prepare(task, video, "PROCESSED_VIDEO_COVER", video.id);
    }

    private AiImageResultEntity prepare(AiVideoTaskEntity task, AiVideoResultEntity video, String targetType, Long targetId) {
        // Reserve a stable image identity so a failed capture/publication can reuse the same original.
        var existing = images.selectList(new LambdaQueryWrapper<AiImageResultEntity>()
            .eq(AiImageResultEntity::getTenantId, task.tenantId)
            .eq(AiImageResultEntity::getProjectId, task.projectId)
            .eq(AiImageResultEntity::getTargetType, targetType)
            .eq(AiImageResultEntity::getTargetId, targetId)
            .in(AiImageResultEntity::getStatus, "PROCESSING", "ACTIVE")
            .orderByAsc(AiImageResultEntity::getId).last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        AiImageResultEntity image;
        if (existing.isEmpty()) {
            image = new AiImageResultEntity();
            image.setTenantId(task.tenantId);
            image.setProjectId(task.projectId);
            image.setTargetType(targetType);
            image.setTargetId(targetId);
            image.setImageUrl("");
            image.setMimeType("image/jpeg");
            image.setIsSelected(false);
            image.setStatus("PROCESSING");
            image.setCreatedAt(now);
            image.setUpdatedAt(now);
            images.insert(image);
        } else {
            image = existing.get(0);
        }
        if (image.getStoragePath() == null || image.getStoragePath().isBlank()) {
            image.setStoragePath(keys.projectOriginal(task.tenantId, task.projectId, "images", image.getId(),
                "video-cover-" + task.id, image.getCreatedAt().toLocalDate(), "jpg"));
        }
        var identity = new MediaObjectIdentity(task.tenantId, task.projectId, "AI_IMAGE_RESULT", image.getId(), "result-" + image.getId());
        var display = covers.create(identity, video.storagePath, image.getStoragePath());
        var original = renditions.originalDetails(identity);
        if (original == null || !"READY".equals(original.status()) || original.fileSize() <= 0) {
            throw new IllegalStateException("视频封面原图尚未就绪。");
        }
        image.setImageUrl("/api/projects/" + task.projectId + "/ai-image-results/" + image.getId() + "/display");
        image.setDisplayPath(display.displayKey());
        image.setThumbnailPath(display.displayKey());
        image.setThumbnailUrl("/api/projects/" + task.projectId + "/ai-image-results/" + image.getId() + "/thumbnail");
        image.setFileSize(original.fileSize());
        image.setWidth(video.width);
        image.setHeight(video.height);
        image.setStatus("ACTIVE");
        image.setUpdatedAt(now);
        video.coverUrl = image.getImageUrl();
        return image;
    }

    /** Called in the same transaction that publishes the generated video. */
    public void publish(AiImageResultEntity image) {
        images.updateById(image);
    }
}
