package com.antshorttv.shot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("storyboard_subtitle")
public class StoryboardSubtitleEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long storyboardId;
    public Long voiceResultId;
    public String subtitleType;
    public String content;
    public String srtUrl;
    public String styleConfig;
    public Boolean isSelected;
    public String status;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
