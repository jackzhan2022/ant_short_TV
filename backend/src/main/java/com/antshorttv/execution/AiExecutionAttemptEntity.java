package com.antshorttv.execution;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_execution_attempt")
public class AiExecutionAttemptEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long executionId;
    public Integer executionVersion;
    public String phase;
    public Integer attemptNo;
    public String status;
    public String idempotencyKey;
    public Boolean providerContacted;
    public Long providerId;
    public Long modelId;
    public String providerRequestId;
    public String externalTaskId;
    public Long aiCallLogId;
    public String transportOutcome;
    public String businessOutcome;
    public Boolean retryable;
    public Integer retryCount;
    public LocalDateTime nextRetryAt;
    public String errorCode;
    public String errorMessage;
    public LocalDateTime startedAt;
    public LocalDateTime providerContactedAt;
    public LocalDateTime finishedAt;
}
