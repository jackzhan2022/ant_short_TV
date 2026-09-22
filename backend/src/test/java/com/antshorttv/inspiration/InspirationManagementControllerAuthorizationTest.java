package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.platform.RequirePlatformPermission;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class InspirationManagementControllerAuthorizationTest {
    @Test
    void everyManagementEndpointRequiresManagePermission() {
        Method[] endpoints = InspirationManagementController.class.getDeclaredMethods();
        assertThat(endpoints).isNotEmpty();
        assertThat(endpoints)
            .allSatisfy(method -> assertThat(method.getAnnotation(RequirePlatformPermission.class))
                .as(method.getName())
                .isNotNull()
                .extracting(RequirePlatformPermission::value)
                .isEqualTo("PLATFORM_INSPIRATION_MANAGE"));
    }
}
