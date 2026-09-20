package com.antshorttv.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiVideoTaskReferenceMapper extends BaseMapper<AiVideoTaskReferenceEntity> {
    default List<AiVideoTaskReferenceEntity> selectByTask(Long tenantId, Long projectId, Long taskId) {
        return selectList(new LambdaQueryWrapper<AiVideoTaskReferenceEntity>()
            .eq(AiVideoTaskReferenceEntity::getTenantId, tenantId)
            .eq(AiVideoTaskReferenceEntity::getProjectId, projectId)
            .eq(AiVideoTaskReferenceEntity::getTaskId, taskId)
            .orderByAsc(AiVideoTaskReferenceEntity::getSortOrder));
    }
}
