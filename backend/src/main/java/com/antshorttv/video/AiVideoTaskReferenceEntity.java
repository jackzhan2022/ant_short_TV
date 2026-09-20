package com.antshorttv.video;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_video_task_reference")
public class AiVideoTaskReferenceEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long taskId;
    public Long storyboardId;
    public String mediaType;
    public Integer mediaIndex;
    public String providerRole;
    public String sourceType;
    public Long sourceId;
    public Long variantId;
    public String displayName;
    public String compiledLabel;
    public String objectStoragePath;
    public String providerUrl;
    public String format;
    public Long fileSize;
    public Integer width;
    public Integer height;
    public BigDecimal durationSeconds;
    public BigDecimal fps;
    public Integer sortOrder;
    public LocalDateTime createdAt;

    public Long getTenantId() { return tenantId; }
    public Long getProjectId() { return projectId; }
    public Long getTaskId() { return taskId; }
    public Integer getSortOrder() { return sortOrder; }
}
