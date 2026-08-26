package com.antshorttv.commercial;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime;
@TableName("commercial_audit") public class CommercialAuditEntity { @TableId(type=IdType.AUTO) public Long id; public Long tenantId; public Long userId; public String operatorType; public String operation; public String targetType; public Long targetId; public String detailJson; public LocalDateTime createdAt; }
