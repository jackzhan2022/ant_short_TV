package com.antshorttv.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("project_member")
public class ProjectMemberEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long userId;
    public Long organizationId;
    public Long roleId;
    public LocalDateTime joinedAt;
    public String status;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
