package com.antshorttv.drama;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(100)
public class DramaSourceProtectionAspect {
    private final DramaSourceProtection protection;
    public DramaSourceProtectionAspect(DramaSourceProtection protection) { this.protection = protection; }

    @Around("execution(* com.antshorttv.shot.ShotProductionService.deleteEpisodeVideoVersion(..)) && args(tenantId, projectId, versionId, ..)")
    public Object protectVersion(ProceedingJoinPoint call, Long tenantId, Long projectId, Long versionId) throws Throwable {
        return protection.deleteVersion(call, tenantId, projectId, versionId);
    }

    @Before("execution(* com.antshorttv.storage.ObjectStorageService.delete(..)) && args(path)")
    public void protectObject(String path) {
        if (path != null && !path.isBlank()) protection.beforeObjectDelete(path);
    }
}
