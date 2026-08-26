package com.antshorttv.accounting;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@TableName("ai_model_point_price_component")
public class AiModelPointPriceComponentEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long priceVersionId;
    public String metric;
    public BigDecimal unitSize;
    public BigDecimal pointRate;
    public String dimensionsJson;
    public String dimensionsKey;
    public LocalDateTime createdAt;
    @TableField(exist = false)
    public Map<String, String> dimensions;
}
