package com.antshorttv.freecreation;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("free_creation")
public class FreeCreationEntity {
    @TableId(type = IdType.AUTO) public Long id;
    public Long tenantId;
    public Long creatorId;
    public Long executionId;
    public String requestId;
    public String requestHash;
    public String mediaType;
    public Long modelId;
    public String modelName;
    public String providerCode;
    public String prompt;
    public String parametersJson;
    public Long avatarRecoverySourceCreationId;
    public String videoRequestSnapshotJson;
    public String externalTaskId;
    public String providerResultJson;
    public Long billingAttemptId;
    public Long billingCallLogId;
    public LocalDateTime providerStartedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
