package com.antshorttv.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("project_ai_config")
public class ProjectAiConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long textModelId;
    private Long imageModelId;
    private Long videoModelId;
    private Long audioModelId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getTextModelId() { return textModelId; }
    public void setTextModelId(Long textModelId) { this.textModelId = textModelId; }
    public Long getImageModelId() { return imageModelId; }
    public void setImageModelId(Long imageModelId) { this.imageModelId = imageModelId; }
    public Long getVideoModelId() { return videoModelId; }
    public void setVideoModelId(Long videoModelId) { this.videoModelId = videoModelId; }
    public Long getAudioModelId() { return audioModelId; }
    public void setAudioModelId(Long audioModelId) { this.audioModelId = audioModelId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
