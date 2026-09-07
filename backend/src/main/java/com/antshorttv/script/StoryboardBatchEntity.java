package com.antshorttv.script;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("storyboard_batch")
public class StoryboardBatchEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long scriptId;
    public String name;
    public String idempotencyKey;
    public Long createdBy;
    public LocalDateTime createdAt;
}
