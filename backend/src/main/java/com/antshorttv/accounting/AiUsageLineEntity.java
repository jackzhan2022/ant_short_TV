package com.antshorttv.accounting;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_usage_line")
public class AiUsageLineEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long executionId;
    public Long attemptId;
    public Long aiCallLogId;
    public Long modelId;
    public String metric;
    public BigDecimal quantity;
    public String unit;
    public String source;
    public String dimensionsJson;
    public String dimensionsKey;
    public LocalDateTime observedAt;
    public Long adjustmentOfUsageLineId;
    public LocalDateTime createdAt;
}
