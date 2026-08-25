package com.antshorttv.shot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_voice_task")
public class AiVoiceTaskEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long storyboardId;
    public String providerCode;
    public String model;
    public String voiceType;
    public String speakerName;
    public String voiceId;
    public String textContent;
    public BigDecimal speed;
    public BigDecimal pitch;
    public BigDecimal volume;
    public String status;
    public String errorMessage;
    public LocalDateTime startedAt;
    public LocalDateTime completedAt;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
