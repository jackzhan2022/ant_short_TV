package com.antshorttv.shot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("episode_video_version")
public class EpisodeVideoVersionEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Integer episodeNo;
    public Long composeTaskId;
    public Integer versionNo;
    public String versionName;
    public String videoUrl;
    public String storagePath;
    public String coverUrl;
    public BigDecimal durationSeconds;
    public Integer width;
    public Integer height;
    public Long fileSize;
    public String format;
    public Long materialId;
    public Boolean isCurrent;
    public String status;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
