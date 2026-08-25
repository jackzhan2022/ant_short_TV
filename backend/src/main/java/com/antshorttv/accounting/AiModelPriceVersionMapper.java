package com.antshorttv.accounting;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelPriceVersionMapper extends BaseMapper<AiModelPriceVersionEntity> {
    default AiModelPriceVersionEntity selectEffective(Long modelId, LocalDateTime observedAt) {
        return selectOne(new QueryWrapper<AiModelPriceVersionEntity>()
            .eq("model_id", modelId)
            .eq("status", "PUBLISHED")
            .le("effective_from", observedAt)
            .and(wrapper -> wrapper.isNull("effective_to").or().gt("effective_to", observedAt))
            .orderByDesc("effective_from")
            .last("limit 1"));
    }

    default List<AiModelPriceVersionEntity> selectPublishedByModel(Long modelId) {
        return selectList(new QueryWrapper<AiModelPriceVersionEntity>()
            .eq("model_id", modelId)
            .eq("status", "PUBLISHED")
            .orderByAsc("effective_from"));
    }
}
