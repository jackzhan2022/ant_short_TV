package com.antshorttv.execution;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_execution_task")
public class AiExecutionTaskEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long userId;
    public Long projectId;
    public String scene;
    public String capability;
    public String businessType;
    public Long businessId;
    public Long requestedModelId;
    public Long resolvedModelId;
    public Long costPriceVersionId;
    public Long pointPriceVersionId;
    public Long commercialSubscriptionId;
    public Long commercialPackageVersionId;
    public BigDecimal preDiscountPoints;
    public BigDecimal discountRate;
    public BigDecimal finalPoints;
    public String redactedInputJson;
    public String status;
    public String phase;
    public Integer progress;
    public Integer executionVersion;
    public Long sourceExecutionId;
    public Long rootExecutionId;
    public String clientIdempotencyKey;
    public String traceId;
    public Integer priority;
    public LocalDateTime nextRunAt;
    public String claimToken;
    public LocalDateTime claimedAt;
    public LocalDateTime heartbeatAt;
    public LocalDateTime claimExpiresAt;
    public Boolean retryable;
    public String resultType;
    public Long resultId;
    public String errorCode;
    public String errorMessage;
    public String usageCostStatus;
    public String providerCostSummaryJson;
    public String pointSettlementStatus;
    public BigDecimal reservedPoints;
    public BigDecimal settledPoints;
    public BigDecimal releasedPoints;
    public LocalDateTime startedAt;
    public LocalDateTime completedAt;
    public LocalDateTime canceledAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
