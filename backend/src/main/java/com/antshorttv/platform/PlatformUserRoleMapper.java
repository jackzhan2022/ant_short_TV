package com.antshorttv.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformUserRoleMapper extends BaseMapper<PlatformUserRoleEntity> {
    default long countByUserId(Long userId) {
        return selectCount(new LambdaQueryWrapper<PlatformUserRoleEntity>()
            .eq(PlatformUserRoleEntity::getUserId, userId));
    }

    default void deleteByUserId(Long userId) {
        delete(new LambdaQueryWrapper<PlatformUserRoleEntity>()
            .eq(PlatformUserRoleEntity::getUserId, userId));
    }

    default PlatformUserRoleEntity selectByUserIdAndRoleId(Long userId, Long roleId) {
        return selectOne(new LambdaQueryWrapper<PlatformUserRoleEntity>()
            .eq(PlatformUserRoleEntity::getUserId, userId)
            .eq(PlatformUserRoleEntity::getRoleId, roleId));
    }
}
