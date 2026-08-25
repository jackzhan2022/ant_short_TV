package com.antshorttv.accounting;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_model_price_component")
public class AiModelPriceComponentEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long priceVersionId;
    public String metric;
    public BigDecimal unitSize;
    public BigDecimal unitPrice;
    public String currency;
    public String dimensionsJson;
    public String dimensionsKey;
    public LocalDateTime createdAt;
}
