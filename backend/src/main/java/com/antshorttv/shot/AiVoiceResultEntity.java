package com.antshorttv.shot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_voice_result")
public class AiVoiceResultEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long taskId;
    public Long storyboardId;
    public String audioUrl;
    public String storagePath;
    public BigDecimal durationSeconds;
    public Long fileSize;
    public String format;
    public Long materialId;
    public Boolean isSelected;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
