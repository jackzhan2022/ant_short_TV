package com.antshorttv.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("project_operation_log")
public class ProjectOperationLogEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long userId;
    public String operationType;
    public String resourceType;
    public Long resourceId;
    public String beforeData;
    public String afterData;
    public String ip;
    public String userAgent;
    public LocalDateTime createdAt;
}
