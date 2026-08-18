package com.antshorttv.shot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("shot_compose_task")
public class ShotComposeTaskEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long storyboardId;
    public Long voiceResultId;
    public Long subtitleId;
    public String composeConfig;
    public String status;
    public String errorMessage;
    public LocalDateTime startedAt;
    public LocalDateTime completedAt;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
