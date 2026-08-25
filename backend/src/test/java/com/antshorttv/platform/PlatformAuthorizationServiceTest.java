package com.antshorttv.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.antshorttv.user.UserStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PlatformAuthorizationServiceTest {

    @Autowired
    private PlatformAuthorizationService authorizationService;

    @Autowired
    private PlatformAdminBootstrap platformAdminBootstrap;

    @Autowired
    private PlatformUserRoleMapper platformUserRoleMapper;

    @Autowired
    private UserMapper userMapper;

    private UserEntity user;

    @BeforeEach
    void createConfiguredAdministrator() {
        UserEntity existing = userMapper.selectByMobile("13800000999");
        if (existing != null) {
            platformUserRoleMapper.deleteByUserId(existing.getId());
            userMapper.deleteById(existing.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        user = new UserEntity();
        user.setMobile("13800000999");
        user.setPasswordHash("{noop}Password123");
        user.setNickname("Platform Admin");
        user.setStatus(UserStatus.ACTIVE.name());
        user.setTokenVersion(0L);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
    }

    @Test
    void controlledBootstrapIsIdempotentAndGrantsPlatformPermissions() {
        platformAdminBootstrap.assignConfiguredAdministrator();
        platformAdminBootstrap.assignConfiguredAdministrator();

        assertThat(platformUserRoleMapper.countByUserId(user.getId())).isEqualTo(1);
        assertThat(authorizationService.roleCodes(user.getId()))
            .containsExactly("PLATFORM_ADMIN");
        assertThat(authorizationService.permissionCodes(user.getId()))
            .contains("PLATFORM_AI_PROVIDER_VIEW", "PLATFORM_AI_MODEL_EDIT");
    }

    @Test
    void ordinaryUserHasNoPlatformPermissionsWithoutExplicitAssignment() {
        assertThat(authorizationService.permissionCodes(user.getId())).isEmpty();
        assertThat(authorizationService.hasPermission(user.getId(), "PLATFORM_AI_PROVIDER_VIEW")).isFalse();
    }
}
