package com.antshorttv.accounting;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelPointPriceVersionMapper extends BaseMapper<AiModelPointPriceVersionEntity> {
    default List<AiModelPointPriceVersionEntity> selectByModel(Long modelId) {
        return selectList(new QueryWrapper<AiModelPointPriceVersionEntity>()
            .eq("model_id", modelId).orderByAsc("version_no"));
    }

    default AiModelPointPriceVersionEntity selectEffective(Long modelId, LocalDateTime at) {
        return selectOne(new QueryWrapper<AiModelPointPriceVersionEntity>()
            .eq("model_id", modelId).eq("status", "PUBLISHED")
            .le("effective_from", at)
            .and(q -> q.isNull("effective_to").or().gt("effective_to", at))
            .orderByDesc("version_no").last("limit 1"));
    }
}
