package com.antshorttv.points;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_point_policy_component")
public class AiPointPolicyComponentEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long policyVersionId;
    public String metric;
    public BigDecimal unitSize;
    public BigDecimal pointRate;
    public String dimensionsJson;
    public String dimensionsKey;
    public LocalDateTime createdAt;
}
