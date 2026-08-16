package com.antshorttv.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("project_role")
public class ProjectRoleEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public String name;
    public String code;
    public String description;
    public Boolean isSystem;
    public String status;
    public String dataScope;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
