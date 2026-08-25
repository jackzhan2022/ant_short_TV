package com.antshorttv.accounting;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelPriceComponentMapper extends BaseMapper<AiModelPriceComponentEntity> {
    default List<AiModelPriceComponentEntity> selectByVersion(Long versionId) {
        return selectList(new QueryWrapper<AiModelPriceComponentEntity>()
            .eq("price_version_id", versionId)
            .orderByAsc("id"));
    }

    default List<AiModelPriceComponentEntity> selectByVersionAndMetric(Long versionId, String metric) {
        return selectList(new QueryWrapper<AiModelPriceComponentEntity>()
            .eq("price_version_id", versionId)
            .eq("metric", metric)
            .orderByAsc("id"));
    }
}
