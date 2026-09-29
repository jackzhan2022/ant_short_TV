package com.antshorttv.script;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("storyboard_asset_reference")
public class StoryboardAssetReferenceEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Long storyboardId;
    public String assetType;
    public Long assetId;
    public Long variantId;
    public String referenceRole;
    public Integer sortOrder;
    public String resolutionStatus;
    public String sourceType;
    public String sourceName;
    public Boolean lockedByUser;
    public Long generatedByRunId;
    public Long createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public LocalDateTime retiredAt;
}
