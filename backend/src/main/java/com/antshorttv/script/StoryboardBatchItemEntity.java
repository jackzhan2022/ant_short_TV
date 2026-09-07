package com.antshorttv.script;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("storyboard_batch_item")
public class StoryboardBatchItemEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long batchId;
    public Long tenantId;
    public Long projectId;
    public Long episodeId;
    public Integer episodeNo;
    public Long executionId;
    public LocalDateTime createdAt;
}
