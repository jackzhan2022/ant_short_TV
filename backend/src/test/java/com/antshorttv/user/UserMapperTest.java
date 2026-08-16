package com.antshorttv.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertsAndFindsUserByMobile() {
        UserEntity user = new UserEntity();
        user.setMobile("13800000001");
        user.setPasswordHash("{bcrypt}hash");
        user.setNickname("张三");
        user.setStatus(UserStatus.ACTIVE.name());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.insert(user);

        UserEntity found = userMapper.selectByMobile("13800000001");
        assertThat(found.getId()).isNotNull();
        assertThat(found.getNickname()).isEqualTo("张三");
    }
}
