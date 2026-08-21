package com.antshorttv.video;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_video_task_attempt")
public class AiVideoTaskAttemptEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long taskId;
    public Integer attemptNo;
    public String phase;
    public String status;
    public String idempotencyKey;
    public String providerRequestId;
    public Long aiCallLogId;
    public Boolean retryable;
    public String errorCode;
    public String errorMessage;
    public LocalDateTime startedAt;
    public LocalDateTime finishedAt;
}
