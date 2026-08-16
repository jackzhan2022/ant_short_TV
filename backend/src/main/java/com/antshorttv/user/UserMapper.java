package com.antshorttv.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    default UserEntity selectByMobile(String mobile) {
        return selectOne(new LambdaQueryWrapper<UserEntity>()
            .eq(UserEntity::getMobile, mobile)
            .isNull(UserEntity::getDeletedAt));
    }
}
