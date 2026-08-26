package com.antshorttv.commercial;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal; import java.time.LocalDateTime;
@TableName("commercial_package_version") public class CommercialPackageVersionEntity { @TableId(type=IdType.AUTO) public Long id; public Long packageId; public Integer versionNo; public String name; public String description; public String billingPeriod; public Integer periodMonths; public BigDecimal price; public BigDecimal listPrice; public String currency; public LocalDateTime effectiveFrom; public LocalDateTime effectiveTo; public String status; public LocalDateTime publishedAt; public Long createdBy; public LocalDateTime createdAt; }
