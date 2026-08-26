package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TeamSubscriptionQueryService {
    private final TeamSubscriptionMapper subscriptionMapper;
    private final CommercialEntitlementGrantMapper grantMapper;

    public TeamSubscriptionQueryService(TeamSubscriptionMapper subscriptionMapper, CommercialEntitlementGrantMapper grantMapper) {
        this.subscriptionMapper = subscriptionMapper;
        this.grantMapper = grantMapper;
    }

    public TeamSubscriptionEntity current(Long tenantId) {
        return subscriptionMapper.selectOne(new QueryWrapper<TeamSubscriptionEntity>()
            .eq("tenant_id", tenantId).eq("status", "ACTIVE")
            .orderByDesc("ends_at").last("limit 1"));
    }

    public List<TeamSubscriptionEntity> queued(Long tenantId) {
        return subscriptionMapper.selectList(new QueryWrapper<TeamSubscriptionEntity>()
            .eq("tenant_id", tenantId).eq("status", "QUEUED").orderByAsc("starts_at"));
    }

    public List<CommercialEntitlementGrantEntity> grants(Long tenantId) {
        return grantMapper.selectList(new QueryWrapper<CommercialEntitlementGrantEntity>()
            .eq("tenant_id", tenantId).orderByDesc("created_at"));
    }
}
