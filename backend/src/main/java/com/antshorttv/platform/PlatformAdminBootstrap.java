package com.antshorttv.platform;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import java.time.LocalDateTime;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformAdminBootstrap implements ApplicationRunner {

    private final PlatformBootstrapProperties properties;
    private final UserMapper userMapper;
    private final PlatformRoleMapper roleMapper;
    private final PlatformUserRoleMapper userRoleMapper;

    public PlatformAdminBootstrap(
        PlatformBootstrapProperties properties,
        UserMapper userMapper,
        PlatformRoleMapper roleMapper,
        PlatformUserRoleMapper userRoleMapper
    ) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        assignConfiguredAdministrator();
    }

    @Transactional
    public void assignConfiguredAdministrator() {
        String mobile = properties.getInitialAdminMobile();
        if (mobile == null || mobile.isBlank()) {
            return;
        }
        UserEntity user = userMapper.selectByMobile(mobile.trim());
        if (user != null) {
            assignIfConfigured(user);
        }
    }

    @Transactional
    public void assignIfConfigured(UserEntity user) {
        String mobile = properties.getInitialAdminMobile();
        if (mobile == null || !mobile.trim().equals(user.getMobile())) {
            return;
        }
        PlatformRoleEntity role = roleMapper.selectActiveByCode("PLATFORM_ADMIN");
        if (role == null || userRoleMapper.selectByUserIdAndRoleId(user.getId(), role.getId()) != null) {
            return;
        }
        PlatformUserRoleEntity assignment = new PlatformUserRoleEntity();
        assignment.setUserId(user.getId());
        assignment.setRoleId(role.getId());
        assignment.setCreatedAt(LocalDateTime.now());
        try {
            userRoleMapper.insert(assignment);
        } catch (DuplicateKeyException ignored) {
            // Another instance completed the same controlled bootstrap.
        }
    }
}
