package com.antshorttv.accounting;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_usage_cost_line")
public class AiUsageCostLineEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long executionId;
    public Long attemptId;
    public Long aiCallLogId;
    public Long usageLineId;
    public Long priceVersionId;
    public Long priceComponentId;
    public Long modelId;
    public String metric;
    public BigDecimal quantity;
    public BigDecimal unitSize;
    public BigDecimal unitPrice;
    public String currency;
    public BigDecimal rawCost;
    public BigDecimal roundedCost;
    public String pricingStatus;
    public String missingReason;
    public Long adjustmentOfCostLineId;
    public LocalDateTime createdAt;
}
