package com.antshorttv.points;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiPointPolicyComponentMapper extends BaseMapper<AiPointPolicyComponentEntity> {
    default List<AiPointPolicyComponentEntity> selectByPolicyVersion(Long policyVersionId) {
        return selectList(new QueryWrapper<AiPointPolicyComponentEntity>()
            .eq("policy_version_id", policyVersionId)
            .orderByAsc("id"));
    }
}
