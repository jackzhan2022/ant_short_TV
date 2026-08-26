package com.antshorttv.accounting;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelPointPriceComponentMapper extends BaseMapper<AiModelPointPriceComponentEntity> {
    default List<AiModelPointPriceComponentEntity> selectByVersion(Long versionId) {
        return selectList(new QueryWrapper<AiModelPointPriceComponentEntity>()
            .eq("price_version_id", versionId).orderByAsc("id"));
    }
}
