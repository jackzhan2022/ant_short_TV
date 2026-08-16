package com.antshorttv.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("project")
public class ProjectEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long organizationId;
    public String name;
    public String code;
    public String description;
    public String coverUrl;
    public Long ownerId;
    public String status;
    public LocalDate startDate;
    public LocalDate endDate;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
