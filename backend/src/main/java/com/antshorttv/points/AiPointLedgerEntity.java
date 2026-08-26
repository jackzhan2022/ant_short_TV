package com.antshorttv.points;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("point_ledger")
public class AiPointLedgerEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long userId;
    public Long executionId;
    public Integer executionVersion;
    public String businessType;
    public Long businessId;
    public Long reservationId;
    public Long attemptId;
    public Long aiCallLogId;
    public Long policyVersionId;
    public String entryType;
    public BigDecimal amount;
    public BigDecimal availableBalanceAfter;
    public BigDecimal reservedBalanceAfter;
    public String idempotencyKey;
    public String description;
    public LocalDateTime createdAt;
}
