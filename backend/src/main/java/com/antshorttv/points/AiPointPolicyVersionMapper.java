package com.antshorttv.points;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiPointPolicyVersionMapper extends BaseMapper<AiPointPolicyVersionEntity> {
    default List<AiPointPolicyVersionEntity> selectPublishedBySelector(
        String scene,
        Long modelId,
        String capability
    ) {
        QueryWrapper<AiPointPolicyVersionEntity> query = new QueryWrapper<AiPointPolicyVersionEntity>()
            .eq("scene", scene)
            .eq("status", "PUBLISHED")
            .orderByAsc("effective_from");
        if (modelId == null) {
            query.isNull("model_id");
        } else {
            query.eq("model_id", modelId);
        }
        if (capability == null) {
            query.isNull("capability");
        } else {
            query.eq("capability", capability);
        }
        return selectList(query);
    }

    default List<AiPointPolicyVersionEntity> selectEffective(String scene, LocalDateTime at) {
        return selectList(new QueryWrapper<AiPointPolicyVersionEntity>()
            .eq("scene", scene)
            .eq("status", "PUBLISHED")
            .le("effective_from", at)
            .and(wrapper -> wrapper.isNull("effective_to").or().gt("effective_to", at))
            .orderByDesc("version_no"));
    }
}
