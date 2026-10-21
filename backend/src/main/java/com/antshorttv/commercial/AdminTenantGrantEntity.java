package com.antshorttv.commercial;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("admin_tenant_package_grant")
public class AdminTenantGrantEntity {
    @TableId(type=IdType.AUTO) public Long id;
    public Long tenantId;
    public Long operatorId;
    public Long packageVersionId;
    public String packageSnapshotJson;
    public Long subscriptionId;
    public String reason;
    public String requestId;
    public String status;
    public LocalDateTime createdAt;
}
