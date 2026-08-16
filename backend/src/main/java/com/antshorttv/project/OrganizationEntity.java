package com.antshorttv.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("organization")
public class OrganizationEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long parentId;
    public String name;
    public String code;
    public Integer level;
    public Long leaderId;
    public Integer sort;
    public String status;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
