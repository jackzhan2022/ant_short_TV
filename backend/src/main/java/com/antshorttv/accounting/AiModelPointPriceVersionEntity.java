package com.antshorttv.accounting;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_model_point_price_version")
public class AiModelPointPriceVersionEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long modelId;
    public Integer versionNo;
    public String status;
    public LocalDateTime effectiveFrom;
    public LocalDateTime effectiveTo;
    public LocalDateTime publishedAt;
    public Long createdBy;
    public LocalDateTime createdAt;
}
