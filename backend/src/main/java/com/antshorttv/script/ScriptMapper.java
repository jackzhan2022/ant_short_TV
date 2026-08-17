package com.antshorttv.script;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScriptMapper extends BaseMapper<ScriptEntity> {
    default ScriptEntity selectCurrentByProject(Long tenantId, Long projectId) {
        return selectOne(new LambdaQueryWrapper<ScriptEntity>()
            .eq(ScriptEntity::getTenantId, tenantId)
            .eq(ScriptEntity::getProjectId, projectId)
            .isNull(ScriptEntity::getDeletedAt)
            .orderByDesc(ScriptEntity::getUpdatedAt)
            .last("limit 1"));
    }
}
