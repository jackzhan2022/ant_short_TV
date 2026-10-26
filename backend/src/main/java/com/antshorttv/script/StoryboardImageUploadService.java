package com.antshorttv.script;

import com.antshorttv.aiimage.AiImageResultMapper;
import com.antshorttv.aiimage.MaterialEntity;
import com.antshorttv.aiimage.MaterialMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.LocalImageUploadService;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryboardImageUploadService {
    private final JdbcTemplate jdbc;
    private final LocalImageUploadService uploads;
    private final MaterialMapper materials;
    private final AiImageResultMapper images;

    public StoryboardImageUploadService(JdbcTemplate jdbc, LocalImageUploadService uploads,
        MaterialMapper materials, AiImageResultMapper images) {
        this.jdbc = jdbc;
        this.uploads = uploads;
        this.materials = materials;
        this.images = images;
    }

    @Transactional
    public UploadedReference upload(Long tenantId, Long projectId, Long storyboardId, Long userId,
        String sessionToken, String name) {
        // Serialize retries for this shot and reject deleted or foreign targets before storing media.
        var targets = jdbc.queryForList("""
            select id from storyboard where id=? and tenant_id=? and project_id=? and deleted_at is null for update
            """, storyboardId, tenantId, projectId);
        if (targets.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "分镜不存在。");
        var image = uploads.storeUpload(tenantId, projectId, "STORYBOARD_REFERENCE", storyboardId, userId, sessionToken);
        if (image.getMaterialId() == null) {
            var material = new MaterialEntity();
            material.setTenantId(tenantId);
            material.setProjectId(projectId);
            material.setMaterialType("IMAGE");
            material.setSourceType("LOCAL_UPLOAD");
            material.setStatus("ACTIVE");
            material.setName(name.trim());
            material.setUrl(image.getImageUrl());
            material.setStoragePath(image.getStoragePath());
            material.setMimeType(image.getMimeType());
            material.setFileSize(image.getFileSize());
            material.setWidth(image.getWidth());
            material.setHeight(image.getHeight());
            material.setCreatedBy(userId);
            material.setCreatedAt(LocalDateTime.now());
            materials.insert(material);
            image.setMaterialId(material.getId());
            images.updateById(image);
        }
        return new UploadedReference(image.getMaterialId(), materials.selectById(image.getMaterialId()).getName());
    }

    public record UploadedReference(Long materialId, String name) {}
}
