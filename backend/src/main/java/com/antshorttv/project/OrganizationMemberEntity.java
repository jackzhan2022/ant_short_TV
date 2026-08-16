package com.antshorttv.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("organization_member")
public class OrganizationMemberEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long organizationId;
    public Long userId;
    public Boolean isPrimary;
    public LocalDateTime joinedAt;
    public LocalDateTime createdAt;
}
