package com.antshorttv.commercial;
import com.baomidou.mybatisplus.annotation.*; import java.math.BigDecimal; import java.time.LocalDateTime;
@TableName("commercial_entitlement") public class CommercialEntitlementEntity { @TableId(type=IdType.AUTO) public Long id; public Long packageVersionId; public String entitlementType; public BigDecimal numericValue; public String textValue; public String configJson; public LocalDateTime createdAt; }
