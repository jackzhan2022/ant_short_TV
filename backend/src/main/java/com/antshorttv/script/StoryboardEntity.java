package com.antshorttv.script;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("storyboard")
public class StoryboardEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long scriptId;
    public Integer episodeNo;
    public Integer shotNo;
    public String visualDescription;
    public String characters;
    public String actions;
    public String scene;
    public Integer durationSeconds;
    public String imagePrompt;
    public String videoPrompt;
    public Long firstFrameImageId;
    public String firstFrameUrl;
    public Long currentVideoResultId;
    public Long currentVideoMaterialId;
    public String currentVideoUrl;
    public String status;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
