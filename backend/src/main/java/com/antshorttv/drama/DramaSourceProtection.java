package com.antshorttv.drama;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Service;

@Service
public class DramaSourceProtection {
    private final DramaReferenceMapper references;
    public DramaSourceProtection(DramaReferenceMapper references) { this.references = references; }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Throwable.class)
    public Object deleteVersion(org.aspectj.lang.ProceedingJoinPoint call, Long tenantId, Long projectId, Long versionId) throws Throwable {
        beforeVersionDelete(tenantId, projectId, versionId);
        return call.proceed();
    }

    public void beforeVersionDelete(Long tenantId, Long projectId, Long versionId) {
        references.lockVersion(tenantId, projectId, versionId);
        if (references.versionReferences(tenantId, versionId) > 0) throw blocked();
    }

    public void beforeObjectDelete(String path) {
        if (references.objectReferences(path) > 0) throw blocked();
    }

    private BusinessException blocked() {
        return new BusinessException(ErrorCode.EPISODE_VIDEO_VERSION_IN_USE, "成片已被独立剧库或发布版本引用，不能删除来源。");
    }
}

@Mapper
interface DramaReferenceMapper {
    @Select("select id from episode_video_version where tenant_id=#{tenantId} and project_id=#{projectId} and id=#{versionId} for update")
    Long lockVersion(Long tenantId, Long projectId, Long versionId);

    @Select("select count(*) from library_episode where tenant_id=#{tenantId} and source_version_id=#{versionId} and deleted_at is null")
    long versionReferences(Long tenantId, Long versionId);

    @Select("select (select count(*) from library_episode where storage_path=#{path} and deleted_at is null) + "
        + "(select count(*) from miniapp_release_episode e join miniapp_drama_release r on r.id=e.release_id where e.storage_path=#{path} and r.published=true)")
    long objectReferences(String path);
}
