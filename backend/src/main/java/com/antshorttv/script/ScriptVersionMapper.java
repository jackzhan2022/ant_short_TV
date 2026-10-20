package com.antshorttv.script;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScriptVersionMapper extends BaseMapper<ScriptVersionEntity> {
    default List<ScriptVersionEntity> selectByScript(Long tenantId, Long scriptId) {
        return selectList(new LambdaQueryWrapper<ScriptVersionEntity>()
            .select(ScriptVersionEntity::getId, ScriptVersionEntity::getScriptId,
                ScriptVersionEntity::getVersionNo, ScriptVersionEntity::getSourceType,
                ScriptVersionEntity::getInputSummary, ScriptVersionEntity::getStatus,
                ScriptVersionEntity::getCreatedAt)
            .eq(ScriptVersionEntity::getTenantId, tenantId)
            .eq(ScriptVersionEntity::getScriptId, scriptId)
            .orderByDesc(ScriptVersionEntity::getVersionNo));
    }

    default Long countByScript(Long tenantId, Long scriptId) {
        return selectCount(new LambdaQueryWrapper<ScriptVersionEntity>()
            .eq(ScriptVersionEntity::getTenantId, tenantId)
            .eq(ScriptVersionEntity::getScriptId, scriptId));
    }

    default ScriptVersionEntity selectByExecutionId(Long executionId) {
        return selectOne(new LambdaQueryWrapper<ScriptVersionEntity>()
            .eq(ScriptVersionEntity::getExecutionId, executionId));
    }
}
