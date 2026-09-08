package com.antshorttv.commercial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("commercial_entitlement_definition")
public class CommercialEntitlementDefinitionEntity {
    @TableId(type = IdType.AUTO) public Long id;
    public String code;
    public String name;
    public String description;
    public String category;
    public String status;
    public Integer sortOrder;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
