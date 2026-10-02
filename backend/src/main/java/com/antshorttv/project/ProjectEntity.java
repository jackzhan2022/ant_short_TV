package com.antshorttv.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
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
    // Cover writes use dedicated SQL so ordinary edits cannot overwrite a newer source/version.
    @TableField(updateStrategy = FieldStrategy.NEVER)
    public String coverUrl;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    public String coverSource;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    public String coverVersion;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    public String coverStatus;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    public String coverError;
    public Long ownerId;
    public String status;
    public LocalDate startDate;
    public LocalDate endDate;
    public String aspectRatio;
    public String videoResolution;
    public Boolean videoGenerateAudio;
    public Boolean videoWatermark;
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
