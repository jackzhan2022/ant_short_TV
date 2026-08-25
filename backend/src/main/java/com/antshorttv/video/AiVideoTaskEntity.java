package com.antshorttv.video;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_video_task")
public class AiVideoTaskEntity {
    public Long executionId;
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long storyboardId;
    public Long serviceConfigId;
    public String providerCode;
    public String model;
    public String prompt;
    public String negativePrompt;
    public Long firstFrameImageId;
    public String firstFrameUrl;
    public Long lastFrameImageId;
    public String lastFrameUrl;
    public String referenceImages;
    public Integer durationSeconds;
    public String aspectRatio;
    public String resolution;
    public String motionStrength;
    public String cameraMovement;
    public Long randomSeed;
    public String externalTaskId;
    public String externalStatus;
    public String status;
    public String errorMessage;
    public String requestHash;
    public Integer pollRetryCount;
    public String executionToken;
    public String executionPhase;
    public Integer executionVersion;
    public LocalDateTime claimedAt;
    public LocalDateTime heartbeatAt;
    public LocalDateTime executionTimeoutAt;
    public Boolean retryable;
    public LocalDateTime submittedAt;
    public LocalDateTime startedAt;
    public LocalDateTime completedAt;
    public LocalDateTime lastPollAt;
    public LocalDateTime nextPollAt;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
