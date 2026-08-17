package com.antshorttv.video;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_video_result")
public class AiVideoResultEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long taskId;
    public Long storyboardId;
    public String videoUrl;
    public String storagePath;
    public String coverUrl;
    public BigDecimal durationSeconds;
    public Integer width;
    public Integer height;
    public Long fileSize;
    public String format;
    public Long materialId;
    public Boolean isSelected;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
