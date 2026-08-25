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
    public String name;
    public String code;
    public String description;
    public String coverUrl;
    public String coverSource;
    public Long ownerId;
    public String status;
    public LocalDate startDate;
    public LocalDate endDate;
    public String aspectRatio;
    public String fileFormat;
    public String scriptType;
    public String breakdownStrength;
    public String visualStyle;
    public String initialScriptContent;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
