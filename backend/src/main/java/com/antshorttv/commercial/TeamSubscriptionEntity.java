package com.antshorttv.commercial;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime;
@TableName("team_subscription") public class TeamSubscriptionEntity { @TableId(type=IdType.AUTO) public Long id; public Long tenantId; public Long packageVersionId; public Long sourceOrderId; public String status; public LocalDateTime startsAt; public LocalDateTime endsAt; public LocalDateTime nextGrantAt; public String snapshotJson; public LocalDateTime createdAt; public LocalDateTime updatedAt; }
