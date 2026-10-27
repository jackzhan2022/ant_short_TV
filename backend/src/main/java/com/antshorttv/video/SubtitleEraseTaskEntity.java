package com.antshorttv.video;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("subtitle_erase_task")
public class SubtitleEraseTaskEntity {
    @TableId(type=IdType.AUTO) public Long id;
    public Long tenantId, projectId, storyboardId, sourceResultId, executionId, resultId, createdBy;
    public String method, quality, regionJson, clientKey, retryKey, providerTaskId, inputUrl, outputUrl, outputResolution;
    public Boolean providerFailed;
    public Integer generation;
    public BigDecimal outputDuration;
    public LocalDateTime outputExpiresAt, deadlineAt, createdAt, updatedAt;
}
