package com.antshorttv.points;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_point_reservation")
public class AiPointReservationEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long userId;
    public Long executionId;
    public Integer executionVersion;
    public String businessType;
    public Long businessId;
    public String scene;
    public Long policyVersionId;
    public Long pointPriceVersionId;
    public BigDecimal discountRate;
    public String status;
    public String authorizedUsageJson;
    public String dimensionsJson;
    public BigDecimal reservedPoints;
    public BigDecimal settledPoints;
    public BigDecimal releasedPoints;
    public BigDecimal refundedPoints;
    public String idempotencyKey;
    public LocalDateTime createdAt;
    public LocalDateTime settledAt;
    public LocalDateTime releasedAt;
    public LocalDateTime refundedAt;
    public LocalDateTime updatedAt;
}
