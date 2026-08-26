package com.antshorttv.commercial;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("commercial_package") public class CommercialPackageEntity { @TableId(type=IdType.AUTO) public Long id; public String code; public String packageType; public String status; public Long createdBy; public LocalDateTime createdAt; public LocalDateTime updatedAt; }
