package com.antshorttv.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("media_processing_job")
class MediaProcessingJobEntity {
    @TableId(type = IdType.AUTO)
    Long id;
    Long tenantId;
    Long projectId;
    Long mediaObjectId;
    String providerJobId;
    String queueId;
    String operation;
    String inputKey;
    String outputKey;
    String callbackTokenHash;
    String correlationData;
    String status;
    Integer attemptNo;
    String errorCode;
    String errorMessage;
    LocalDateTime submittedAt;
    LocalDateTime completedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
