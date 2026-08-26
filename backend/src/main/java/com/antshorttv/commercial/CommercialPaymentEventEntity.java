package com.antshorttv.commercial;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime;
@TableName("commercial_payment_event") public class CommercialPaymentEventEntity { @TableId(type=IdType.AUTO) public Long id; public Long orderId; public String provider; public String eventType; public String providerEventId; public String payloadJson; public Boolean processed; public LocalDateTime createdAt; }
