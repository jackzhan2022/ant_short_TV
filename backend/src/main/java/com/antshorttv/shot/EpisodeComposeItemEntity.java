package com.antshorttv.shot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("episode_compose_item")
public class EpisodeComposeItemEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long taskId;
    public Integer episodeNo;
    public Long storyboardId;
    public Integer storyboardOrder;
    public Long shotResultId;
    public String videoUrl;
    public BigDecimal durationSeconds;
    public Integer width;
    public Integer height;
    public String status;
    public String errorMessage;
    public LocalDateTime createdAt;
}
