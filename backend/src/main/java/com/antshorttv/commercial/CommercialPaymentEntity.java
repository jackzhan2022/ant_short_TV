package com.antshorttv.commercial;
import com.baomidou.mybatisplus.annotation.*; import java.math.BigDecimal; import java.time.LocalDateTime;
@TableName("commercial_payment") public class CommercialPaymentEntity { @TableId(type=IdType.AUTO) public Long id; public Long orderId; public String provider; public String providerTradeNo; public String prepayId; public String codeUrl; public BigDecimal amount; public String status; public LocalDateTime paidAt; public String rawResponseJson; public LocalDateTime createdAt; public LocalDateTime updatedAt; }
