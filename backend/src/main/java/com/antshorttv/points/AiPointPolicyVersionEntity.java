package com.antshorttv.points;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_point_policy_version")
public class AiPointPolicyVersionEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String scene;
    public Long modelId;
    public String capability;
    public Integer versionNo;
    public String status;
    public LocalDateTime effectiveFrom;
    public LocalDateTime effectiveTo;
    public Boolean chargeProviderRejection;
    public Boolean chargeProviderBilledFailure;
    public Boolean chargeTimeout;
    public Boolean chargeBusinessFailure;
    public LocalDateTime createdAt;
    public LocalDateTime publishedAt;
}
