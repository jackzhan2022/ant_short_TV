package com.antshorttv.platform;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequirePlatformPermissionAspect {

    private final PlatformPermissionGuard guard;

    public RequirePlatformPermissionAspect(PlatformPermissionGuard guard) {
        this.guard = guard;
    }

    @Before("@annotation(requirePlatformPermission)")
    public void require(RequirePlatformPermission requirePlatformPermission) {
        guard.require(requirePlatformPermission.value());
    }
}
