package com.antshorttv.shot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("episode_compose_task")
public class EpisodeComposeTaskEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Integer episodeNo;
    public String taskName;
    public String composeConfig;
    public Integer storyboardCount;
    public BigDecimal totalDurationSeconds;
    public String status;
    public String errorMessage;
    public LocalDateTime startedAt;
    public LocalDateTime completedAt;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
