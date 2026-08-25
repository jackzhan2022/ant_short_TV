package com.antshorttv.script;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("script_ai_operation")
public class ScriptAiOperationEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public String operationType;
    public Long scriptId;
    public Long scriptVersionId;
    public String redactedInputJson;
    public String idempotencyKey;
    public String status;
    public Long executionId;
    public String resultType;
    public Long resultId;
    public String errorCode;
    public String errorMessage;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime completedAt;
}
